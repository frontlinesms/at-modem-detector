package net.frontlinesms.messaging;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import serial.*;

public class ATDeviceDetector extends Thread {
	private static final String STRIP_REGEX = "(\\s+OK)|" +
			// RSSI is "received signal strength indicator", and appears to be received unbidden
			"(\\s+\\^RSSI:\\d+)|" +
			// Don't know what BOOT is, but definitely not part of a device manufacturer ;¬)
			"(\\^BOOT:\\d+(,\\d+)*)";
	private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");
					
	/** Valid baud rates */
	private static final int[] BAUD_RATES = { 9600, 14400, 19200, 28800, 33600, 38400, 56000, 57600, 115200, 230400, 460800, 921600 };

	/** Logger */
	private final Logger log = new Logger(this.getClass());
	
//> DETECTION PROPERTIES
	/** Port this is detecting on */
	private final CommPortIdentifier portIdentifier;
	/** Detection listener */
	private final ATDeviceDetectorListener listener;
	/** The top speed the device was detected at. */
	private int maxBaudRate;
	/** <code>true</code> when the detection thread has finished. */
	private boolean finished;
	
	private String exceptionMessage;
	
//> DEVICE PROPERTIES	
	/** The serial number of the detected device. */
	private String serial;
	private String manufacturer;
	private String model;
	private String imsi;
	private String phoneNumber;
	private String lockType;
	private boolean smsReceiveSupported;
	private boolean smsSendSupported;
	
	public ATDeviceDetector(CommPortIdentifier port, ATDeviceDetectorListener listener) {
		super("ATDeviceDetector: " + port.getName());
		this.portIdentifier = port;
		this.listener = listener;
	}
	
	public void run() {
		for(int baud : BAUD_RATES) {
			SerialPort serialPort = null;
			InputStream in = null;
			OutputStream out = null;
			
			/* This detection workflow was taken from ComTest in SMSLib, and is licensed under Apache v2. */
			try {
				serialPort = portIdentifier.open("ATDeviceDetector", 2000);
				serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN);
				serialPort.setSerialPortParams(baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
				in = serialPort.getInputStream();
				out = serialPort.getOutputStream();
				serialPort.enableReceiveTimeout(1000);
				
				log.trace("LOOPING.");
				
				// discard all data currently waiting on the input stream
				Utils.readAll(in);
				Utils.writeCommand(out, "AT");
				Thread.sleep(1000);
				String response = Utils.readAll(in);
				if(!Utils.isResponseOk(response)) {
					throw new ATDeviceDetectionException("Bad response: " + response);
				}

				serial = getSerial(in, out);

				maxBaudRate = Math.max(maxBaudRate, baud);
				
				// detection is complete, so let's try and get the device manufacturer, model and phone number
				manufacturer = getManufacturer(in, out);
				model = getModel(in, out);
				phoneNumber = getPhoneNumber(in, out);
				lockType = getLockType(in, out);
				setSmsSupport(in, out);
				imsi = getImsi(in, out);
			} catch(InterruptedException ex) {
				log.info("Detection thread interrupted.", ex);
				this.exceptionMessage = "Detection interrupted.";
				break;
			} catch(Throwable t) {
				log.info("Problem connecting to device.", t);
				this.exceptionMessage = t.getMessage();
			} finally {
				// Close any open streams
				if(out != null) try { out.close(); } catch(Throwable t) { log.warn("Error closing output stream.", t); }
				if(in != null) try { in.close(); } catch(Throwable t) { log.warn("Error closing input stream.", t); }
				// N.B. apprently serialPort.close() can result in System.exit from JNI on some systems.  However,
				// without it we cannot connect to ports after detector has run on them
				if(serialPort != null) try { serialPort.close(); } catch(Throwable t) { log.warn("Error closing input stream.", t); }
			}
		}
		finished = true;
		log.info("Detection completed on port: " + this.portIdentifier.getName() +
				"; manufacturer: " + manufacturer +
				"; model: " + model +
				"; phoneNumber: " + phoneNumber);
		if(listener != null) listener.handleDetectionCompleted(this);
	}

	String getSerial(InputStream in, OutputStream out) throws IOException, ATDeviceDetectionException {
		Utils.writeCommand(out, "AT+CGSN");
		String response = Utils.readAll(in);
		if(!Utils.isResponseOk(response)) {
			throw new ATDeviceDetectionException("Bad response to request for serial number: " + response);
		} else {
			String serial = Utils.trimResponse("AT+CGSN", response);
			log.debug("Found serial: " + serial);
			if(this.serial != null) {
				// There was already a serial detected.  Check if it's the same as
				// what we've just got.
				if(!this.serial.equals(serial)) {
					log.info("New serial detected: '" + serial + "'.  Replacing previous: '" + this.serial + "'");
				}
			}
			return serial.replaceAll(STRIP_REGEX, "").trim();
		}
	}
	
	String getManufacturer(InputStream in, OutputStream out) throws IOException {
		return getOptional(in, out, "CGMI");
	}

	String getModel(InputStream in, OutputStream out) throws IOException {
		return getOptional(in, out, "CGMM");
	}

	String getPhoneNumber(InputStream in, OutputStream out) throws IOException {
		String response = getOptional(in, out, "CNUM");
		if(response == null) {
			return null;
		} else {
			Matcher m = Pattern.compile("\\+?\\d+").matcher(response);
			if(m.find()) return m.group();
			else return response;
		}
	}
	
	String getLockType(InputStream in, OutputStream out) throws IOException {
		String response = Utils.executeAtCommand(in, out, "CPIN?", true);
		if(!response.startsWith(": ")) {
			return "UNKNOWN (" + response.trim() + ")";
		} else {
			String type = response.replaceAll(STRIP_REGEX, "").trim()
					.substring(": ".length()).toUpperCase();
			if(type.equals("READY")) {
				return null;
			} else {
				return type;
			}
		}
	}

	String getImsi(InputStream in, OutputStream out) throws IOException {
		return getOptional(in, out, "CIMI");
	}

	void setSmsSupport(InputStream in, OutputStream out) throws IOException {
		String response = Utils.executeAtCommand(in, out, "CSMS?", false);
		smsReceiveSupported = isSmsReceiveSupported(response);
		smsSendSupported = isSmsSendSupported(response);
	}
	
	/** @return value or <code>null</code> */
	String getOptional(InputStream in, OutputStream out, String atCommand) throws IOException {
		String response = Utils.executeAtCommand(in, out, atCommand, true);
		if(response.contains("ERROR")) {
			return null;
		} else {
			return response.replaceAll(STRIP_REGEX, "").trim();
		}
	}

	/** @return <code>true</code> if mobile-terminated SMS is supported; <code>false</code> otherwise */
	boolean isSmsReceiveSupported(String atResponse) {
		return getDigitAsBoolean(atResponse, 1);
	}

	/** @return <code>true</code> if mobile-originated SMS is supported; <code>false</code> otherwise */
	boolean isSmsSendSupported(String atResponse) {
		return getDigitAsBoolean(atResponse, 2);
	}

	private boolean getDigitAsBoolean(String values, int index) {
		Matcher m = DIGIT_PATTERN.matcher(values);

		for(int i=0; m.find(); ++i) {
			if(i == index) return m.group().equals("1");
		}
		return false;
	}

//> ACCESSORS
	public boolean isFinished() {
		return finished;
	}
	
	public boolean isDetected() {
		return this.maxBaudRate > 0;
	}

	public CommPortIdentifier getPortIdentifier() {
		return portIdentifier;
	}

	public String getPortName() {
		return portIdentifier.getName();
	}
	
	public int getMaxBaudRate() {
		return maxBaudRate;
	}
	
	public String getSerial() {
		assert(isDetected()) : "Cannot get serial if no device was detected.";
		return serial;
	}
	
	public String getExceptionMessage() {
		assert(!isDetected()) : "Cannot get Throwable clause if device was detected successfully.";
		return exceptionMessage;
	}
	
	public String getImsi() {
		return imsi;
	}

	public String getManufacturer() {
		return manufacturer;
	}

	public String getModel() {
		return model;
	}
		
	public String getPhoneNumber() {
		return phoneNumber;
	}
	
	public boolean isUnlockRequired() {
		return lockType != null;
	}
	
	public String getLockType() {
		return lockType;
	}

	public boolean isSmsSendSupported() {
		return smsSendSupported;
	}

	public boolean isSmsReceiveSupported() {
		return smsReceiveSupported;
	}
}

