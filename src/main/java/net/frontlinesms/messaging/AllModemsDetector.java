package net.frontlinesms.messaging;

import java.io.*;
import java.util.*;

import serial.*;

/**
 * A commandline utility for detecting connected AT devices.
 * @author Alex Anderson alex@frontlinesms.com
 */
public class AllModemsDetector {
	public static void main(String[] args) {
		SerialClassFactory.init();
		AllModemsDetector amd = new AllModemsDetector();
		ATDeviceDetector[] detectors = amd.detectBlocking();
		printReport(detectors);
	}
	
	private Logger log = new Logger(getClass());
	
	private ATDeviceDetector[] detectors;
	
	/** Trigger detection, and return the results when it is completed. */
	public ATDeviceDetector[] detectBlocking() {
		detect();
		waitUntilDetectionComplete(detectors);
		return getDetectors();
	}
	
	/** Trigger detection. */
	public void detect() {
		log.trace("Starting device detection...");
		Set<ATDeviceDetector> detectors = new HashSet<ATDeviceDetector>();
		Enumeration<CommPortIdentifier> ports = CommPortIdentifier.getPortIdentifiers();
		while(ports.hasMoreElements()) {
			CommPortIdentifier port = ports.nextElement();
			if(port.getPortType() == CommPortIdentifier.PORT_SERIAL) {
				ATDeviceDetector d = new ATDeviceDetector(port);
				detectors.add(d);
				d.start();
			} else {
				log.info("Ignoring non-serial port: " + port.getName());
			}
		}
		this.detectors = detectors.toArray(new ATDeviceDetector[0]);
		log.trace("All detectors started.");
	}
	
	/** Get the detectors. */
	public ATDeviceDetector[] getDetectors() {
		return detectors;
	}
	
	/** Blocks until all detectors have completed execution. */
	private static void waitUntilDetectionComplete(ATDeviceDetector[] detectors) {
		boolean completed;
		do {
			completed = true;
			for (ATDeviceDetector portDetector : detectors) {
				if(!portDetector.isFinished()) {
					completed = false;
				}
			}
			Utils.sleep(500);
		} while(!completed);
	}
	
	/** Prints a report to {@link System#out} detailing the devices that were detected. */
	private static void printReport(ATDeviceDetector[] completedDetectors) {
		// All detectors are finished, so print a report
		for(ATDeviceDetector d : completedDetectors) {
			System.out.println("---");
			System.out.println("PORT   : " + d.getPortIdentifier().getName());
			if(d.isDetected()) {
				System.out.println("SERIAL : " + d.getSerial());
				System.out.println("BAUD   : " + d.getMaxBaudRate());
			} else {
				System.out.println("DETECTION FAILED");
				System.out.println("> " + d.getExceptionMessage());
			}
		}	
	}
}