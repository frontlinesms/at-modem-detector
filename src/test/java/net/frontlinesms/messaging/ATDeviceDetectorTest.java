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
		final String[][] testPairs = new String[][] {
				/* { <modemOutput>, <expectedResponse> } */
				{ "WAVECOM MODEM\rOK", "WAVECOM MODEM" },
				{ "huawei\r\n\r\nOK", "huawei" },
				{ "huawei\r\n\r\nOK\r\n\r\n^RSSI:18", "huawei" },
		};
		
		for(String[] pair : testPairs) {
			testGetManufacturer(pair[0], pair[1]);
		}
	}
	
	private void testGetManufacturer(String modemOutput, String expectedResponse) throws Exception {
		// given
		in = mockInputStream(modemOutput);
		
		// when
		String actualResponse = d.getManufacturer(in, out);
		
		// then
		assertEquals(expectedResponse, actualResponse);
	}
	
	public void testGetLockType() throws Exception {
		final String[][] testPairs = new String[][] {
				/* <modemOutput>, <execptedResult> */
				{ "+CPIN: READY", null },
				{ "+CPIN: SIM PIN", "SIM PIN" },
				{ "+CPIN: PUK2", "PUK2" },
				{ "CMS ERROR: 123", "UNKNOWN" },
		};
				
		for(String[] pair : testPairs) {
			testGetLockType(pair[0], pair[1]);
		}
	}
	
	private void testGetLockType(String modemOutput, String expectedResponse) throws Exception {
		// given
		in = mockInputStream(modemOutput);
		
		// when
		String actualResponse = d.getLockType(in, out);
		
		// then
		assertEquals(expectedResponse, actualResponse);
	}
	
	public void testGetModem() throws Exception {
		final String[][] testPairs = new String[][] {
				/* { <modemOutput>, <expectedResponse> } */
				{ "900P\rOK", "900P" },
				{ "E173\r\n\r\nOK", "E173" },
		};
		
		for(String[] pair : testPairs) {
			testGetModel(pair[0], pair[1]);
		}
	}

	private void testGetModel(String modemOutput, String expectedResponse) throws Exception {
		// given
		in = mockInputStream(modemOutput);
		
		// when
		String actualResponse = d.getModel(in, out);
		
		// then
		assertEquals(expectedResponse, actualResponse);
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
	
	public void testGetPhoneNumber_error() throws Exception {
		// given
		in = mockInputStream("ERROR");
		
		// when
		String response = d.getPhoneNumber(in, out);
		
		// then
		assertEquals(null, response);
	}

	private InputStream mockInputStream(String string) throws UnsupportedEncodingException {
		return new ByteArrayInputStream(string.getBytes("UTF-8"));
	}
}