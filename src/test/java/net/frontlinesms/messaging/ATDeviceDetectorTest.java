package net.frontlinesms.messaging;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import serial.CommPortIdentifier;
import serial.mock.MockSerial;

import net.frontlinesms.junit.BaseTestCase;

import static org.mockito.Mockito.*;

public class ATDeviceDetectorTest extends BaseTestCase {
	private ATDeviceDetector d;
	private InputStream in;
	private OutputStream out;
	
	public void setUp() {
		MockSerial.reset();
		CommPortIdentifier cpi = mock(CommPortIdentifier.class);
		when(cpi.getName()).thenReturn("/dev/mock");
		d = new ATDeviceDetector(cpi);
		out = mock(OutputStream.class);
	}
	
	public void testGetOptional_error() throws Exception {
		// given
		in = mockInputStream("ERROR: 1");
		
		// when
		String response = d.getOptional(in, out, "RANDOM_COMMAND");
		
		// then
		assertNull(response);
	}
	
	public void testGetOptional_ok() throws Exception {
		// given
		in = mockInputStream("asdf\rOK");
		
		// when
		String response = d.getOptional(in, out, "RANDOM_COMMAND");
		
		// then
		assertEquals("asdf", response);
	}
	
	public void testGetManufacturer() throws Exception {
		// given
		in = mockInputStream("WAVECOM MODEM\rOK");
		
		// when
		String response = d.getManufacturer(in, out);
		
		// then
		assertEquals("WAVECOM MODEM", response);
	}

	public void testGetModel() throws Exception {
		// given
		in = mockInputStream("900P\rOK");
		
		// when
		String response = d.getModel(in, out);
		
		// then
		assertEquals("900P", response);
	}
	
	public void testGetPhoneNumber_local() throws Exception {
		// given
		in = mockInputStream("\"Phone\", \"0712345678\",129\rOK");
		
		// when
		String response = d.getPhoneNumber(in, out);
		
		// then
		assertEquals("0712345678", response);
	}
	
	public void testGetPhoneNumber_international() throws Exception {
		// given
		in = mockInputStream("\"Phone\", \"+44712345678\",129\rOK");
		
		// when
		String response = d.getPhoneNumber(in, out);
		
		// then
		assertEquals("+44712345678", response);
	}

	private InputStream mockInputStream(String string) throws UnsupportedEncodingException {
		return new ByteArrayInputStream(string.getBytes("UTF-8"));
	}
}