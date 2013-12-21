/*
 * Copyright 2013 Martin Winandy
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.pmw.tinylog.writers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.pmw.tinylog.AbstractTest;
import org.pmw.tinylog.LoggingLevel;
import org.pmw.tinylog.util.FileHelper;

/**
 * Tests for the shared file logging writer.
 * 
 * @see FileWriter
 */
public class SharedFileWriterTest extends AbstractTest {

	/**
	 * Test writing without threading.
	 * 
	 * @throws IOException
	 *             Test failed
	 */
	@Test
	public final void testSingleThreadedWriting() throws IOException {
		File file = FileHelper.createTemporaryFile(null);
		SharedFileWriter writer = new SharedFileWriter(file.getAbsolutePath());
		writer.init();
		writer.write(LoggingLevel.INFO, "Hello\n");
		writer.write(LoggingLevel.INFO, "World\n");
		writer.close();

		try {
			writer.write(LoggingLevel.INFO, "Won't be written\n");
			fail("Exception expected");
		} catch (IOException ex) {
			// Expected
		}

		BufferedReader reader = new BufferedReader(new FileReader(file));
		assertEquals("Hello", reader.readLine());
		assertEquals("World", reader.readLine());
		assertNull(reader.readLine());
		reader.close();

		file.delete();
	}

	/**
	 * Test writing with threading.
	 * 
	 * @throws IOException
	 *             Test failed
	 * @throws InterruptedException
	 *             Sleep failed
	 */
	@Test
	public final void testMultiThreadedWriting() throws IOException, InterruptedException {
		File file = FileHelper.createTemporaryFile(null);

		List<WritingThread> threads = new ArrayList<SharedFileWriterTest.WritingThread>();
		for (int i = 0; i < 5; ++i) {
			threads.add(new WritingThread(file));
		}

		for (WritingThread thread : threads) {
			thread.start();
		}

		Thread.sleep(100L);

		for (WritingThread thread : threads) {
			thread.shutdown();
		}

		for (WritingThread thread : threads) {
			thread.join();
		}

		long writtenLines = 0L;
		for (WritingThread thread : threads) {
			writtenLines += thread.getWrittenLines();
		}

		long readLines = 0L;
		BufferedReader reader = new BufferedReader(new FileReader(file));
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			assertEquals(WritingThread.LINE, line);
			++readLines;
		}
		reader.close();

		assertNotEquals(0, readLines);
		assertEquals(writtenLines, readLines);

		file.delete();
	}

	/**
	 * Test overwriting of existing log file.
	 * 
	 * @throws IOException
	 *             Test failed
	 */
	@Test
	public final void testOverwriting() throws IOException {
		File file = FileHelper.createTemporaryFile(null);

		/* Overwriting by first writer */

		FileHelper.write(file, "Hello World!");

		BufferedReader reader = new BufferedReader(new FileReader(file));
		assertEquals("Hello World!", reader.readLine());
		assertNull(reader.readLine());
		reader.close();

		SharedFileWriter writer = new SharedFileWriter(file.getAbsolutePath());
		writer.init();
		writer.close();

		reader = new BufferedReader(new FileReader(file));
		assertNull(reader.readLine());
		reader.close();

		/* But no overwriting by second writer */

		SharedFileWriter writer1 = new SharedFileWriter(file.getAbsolutePath());
		writer1.init();
		writer1.write(LoggingLevel.INFO, "Hello\n");

		SharedFileWriter writer2 = new SharedFileWriter(file.getAbsolutePath());
		writer2.init();
		writer2.write(LoggingLevel.INFO, "World\n");

		writer1.close();
		writer2.close();

		reader = new BufferedReader(new FileReader(file));
		assertEquals("Hello", reader.readLine());
		assertEquals("World", reader.readLine());
		assertNull(reader.readLine());
		reader.close();

		file.delete();
	}

	private static final class WritingThread extends Thread {

		private static final String LINE = "!!! Hello World! !!! qwertzuiopasdfghjklyxcvbnm !!!";

		private final SharedFileWriter writer;
		private long writtenLines;
		private volatile boolean shutdown;

		public WritingThread(final File file) throws IOException {
			writer = new SharedFileWriter(file.getAbsolutePath());
			writer.init();
			writtenLines = 0L;
			shutdown = false;
		}

		public long getWrittenLines() {
			return writtenLines;
		}

		@Override
		public void run() {
			try {
				while (!shutdown) {
					writer.write(LoggingLevel.INFO, LINE + "\n");
					++writtenLines;
				}
				writer.close();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}

		public void shutdown() {
			this.shutdown = true;
		}

	}

}