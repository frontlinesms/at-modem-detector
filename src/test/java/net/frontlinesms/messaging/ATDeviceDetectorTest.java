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
		d = new ATDeviceDetector(cpi, null);
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

	public void testGetImsi_ok() throws Exception {
		// given
		in = mockInputStream("012345678901234\r\nOK");

		// when
		String response = d.getImsi(in, out);

		// then
		assertEquals("012345678901234", response);
	}

	public void testGetSerial() throws Exception {
		final String[][] testPairs = new String[][] {
				/* { <modemOutput>, <expectedResponse> } */
				{ "123457890\rOK", "123457890" },
				{ "123457890\r\n\r\nOK", "123457890" },
				{ "123457890\r\n\r\nOK\r\n\r\n^RSSI:18", "123457890"},
				{ "123457890\r\n\r\nOK\r\n\r\n^BOOT:9716548,0,0,0,20", "123457890" },
				{ "85558555\r\n\r\nOK\r\n\r\n+CREG: 1\r\n\r\n+CGREG: 1", "85558555" },
		};

		for(String[] pair : testPairs) {
			testGetSerial(pair[0], pair[1]);
		}
	}

	private void testGetSerial(String modemOutput, String expectedResponse) throws Exception {
		// given
		in = mockInputStream(modemOutput);

		// when
		String actualResponse = d.getSerial(in, out);

		// then
		assertEquals(expectedResponse, actualResponse);
	}

	public void testGetManufacturer() throws Exception {
		final String[][] testPairs = new String[][] {
				/* { <modemOutput>, <expectedResponse> } */
				{ "WAVECOM MODEM\rOK", "WAVECOM MODEM" },
				{ "huawei\r\n\r\nOK", "huawei" },
				{ "huawei\r\n\r\nOK\r\n\r\n^RSSI:18", "huawei"},
				{ "huawei\r\n\r\nOK\r\n\r\n^BOOT:9716548,0,0,0,20", "huawei" },
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
				{ "+CPIN: SIM PIN\r\n\r\nOK", "SIM PIN" },
				{ "\r\n+CPIN: PUK2", "PUK2" },
				{ "\r\nCMS ERROR: 123\r\n", "UNKNOWN (CMS ERROR: 123)" },
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

	public void testSmsSendSupported_yes() throws Exception {
		// expect
		assertTrue(d.isSmsSendSupported("SMS: 0,0,1,0\r\rOK"));
		assertTrue(d.isSmsSendSupported("SMS: 0,0,1,1\r\rOK"));
		assertTrue(d.isSmsSendSupported("SMS: 0,1,1,0\r\rOK"));
		assertTrue(d.isSmsSendSupported("SMS: 0,1,1,1\r\rOK"));
		assertTrue(d.isSmsSendSupported("SMS: 1,0,1,0\r\rOK"));
		assertTrue(d.isSmsSendSupported("SMS: 1,0,1,1\r\rOK"));
		assertTrue(d.isSmsSendSupported("SMS: 1,1,1,0\r\rOK"));
		assertTrue(d.isSmsSendSupported("SMS: 1,1,1,1\r\rOK"));
	}

	public void testSmsSendSupported_no() throws Exception {
		// expect
		assertFalse(d.isSmsSendSupported("SMS: 0,0,0,0\r\rOK"));
		assertFalse(d.isSmsSendSupported("SMS: 0,0,0,1\r\rOK"));
		assertFalse(d.isSmsSendSupported("SMS: 0,1,0,0\r\rOK"));
		assertFalse(d.isSmsSendSupported("SMS: 0,1,0,1\r\rOK"));
		assertFalse(d.isSmsSendSupported("SMS: 1,0,0,0\r\rOK"));
		assertFalse(d.isSmsSendSupported("SMS: 1,0,0,1\r\rOK"));
		assertFalse(d.isSmsSendSupported("SMS: 1,1,0,0\r\rOK"));
		assertFalse(d.isSmsSendSupported("SMS: 1,1,0,1\r\rOK"));
	}

	public void testSmsSendSupported_error() throws Exception {
		// expect
		assertFalse(d.isSmsSendSupported("ERROR"));
	}

	public void testSmsReceiveSupported_yes() throws Exception {
		// expect
		assertTrue(d.isSmsReceiveSupported("SMS: 0,1,0,0\r\rOK"));
		assertTrue(d.isSmsReceiveSupported("SMS: 0,1,0,1\r\rOK"));
		assertTrue(d.isSmsReceiveSupported("SMS: 0,1,1,0\r\rOK"));
		assertTrue(d.isSmsReceiveSupported("SMS: 0,1,1,1\r\rOK"));
		assertTrue(d.isSmsReceiveSupported("SMS: 1,1,0,0\r\rOK"));
		assertTrue(d.isSmsReceiveSupported("SMS: 1,1,0,1\r\rOK"));
		assertTrue(d.isSmsReceiveSupported("SMS: 1,1,1,0\r\rOK"));
		assertTrue(d.isSmsReceiveSupported("SMS: 1,1,1,1\r\rOK"));
	}

	public void testSmsReceiveSupported_no() throws Exception {
		// expect
		assertFalse(d.isSmsReceiveSupported("SMS: 0,0,0,0\r\rOK"));
		assertFalse(d.isSmsReceiveSupported("SMS: 0,0,0,1\r\rOK"));
		assertFalse(d.isSmsReceiveSupported("SMS: 0,0,1,0\r\rOK"));
		assertFalse(d.isSmsReceiveSupported("SMS: 0,0,1,1\r\rOK"));
		assertFalse(d.isSmsReceiveSupported("SMS: 1,0,0,0\r\rOK"));
		assertFalse(d.isSmsReceiveSupported("SMS: 1,0,0,1\r\rOK"));
		assertFalse(d.isSmsReceiveSupported("SMS: 1,0,1,0\r\rOK"));
		assertFalse(d.isSmsReceiveSupported("SMS: 1,0,1,1\r\rOK"));
	}

	public void testSmsReceiveSupported_error() throws Exception {
		// expect
		assertFalse(d.isSmsReceiveSupported("ERROR"));
	}

//> TEST SETUP METHODS
	private InputStream mockInputStream(String string) throws UnsupportedEncodingException {
		return new ByteArrayInputStream(string.getBytes("UTF-8"));
	}
}
