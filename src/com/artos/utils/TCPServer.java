// Copyright <2018> <Arpitos>

// Permission is hereby granted, free of charge, to any person obtaining a copy of this software
// and associated documentation files (the "Software"), to deal in the Software without restriction,
// including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
// subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
// INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
// IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
// WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
// OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
package com.arpitos.utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.arpitos.interfaces.Connectable;
import com.arpitos.interfaces.ConnectableFilter;

/**
 * This class listens for client connection and accepts single client connection
 * with server
 * 
 * 
 *
 */
public class TCPServer implements Connectable {
	int nPort;
	ServerSocket tcpSocket;
	Socket serverSocket;
	BufferedReader inFromClient;
	DataOutputStream outToClient;
	Queue<byte[]> queue = new LinkedList<byte[]>();
	List<ConnectableFilter> filterList = null;

	/**
	 * Constructor
	 * 
	 * @param nPort
	 *            Port Number, or 0 to use a port number that is automatically
	 *            allocated
	 */
	public TCPServer(int nPort) {
		this.nPort = nPort;
		this.filterList = null;
	}

	/**
	 * Constructor. Every filter adds overheads in processing received messages
	 * which may have impact on performance
	 * 
	 * @param nPort
	 *            Port Number, or 0 to use a port number that is automatically
	 *            allocated
	 * @param filterList
	 *            list of filters
	 */
	public TCPServer(int nPort, List<ConnectableFilter> filterList) {
		this.nPort = nPort;
		this.filterList = filterList;
	}

	/**
	 * Creates a server socket, bound to the specified port. The method blocks
	 * until a connection is made.
	 * 
	 * @throws IOException
	 *             if an I/O error occurs when opening the socket.
	 */
	public void connect() throws IOException {
		// set infinite timeout by default
		connect(0);
	}

	/**
	 * Creates a server socket, bound to the specified port. The method blocks
	 * until a connection is made.
	 * 
	 * Setting soTimeout to a non-zero timeout, a call to accept() for this
	 * ServerSocket will block for only this amount of time. If the timeout
	 * expires, a java.net.SocketTimeoutException is raised, though the
	 * ServerSocket is still valid.
	 * 
	 * @param soTimeout
	 *            the specified timeout in milliseconds.
	 * @throws IOException
	 *             if an I/O error occurs when opening the socket.
	 */
	public void connect(int soTimeout) throws IOException {
		System.out.println("Listening on Port : " + nPort);

		tcpSocket = new ServerSocket(nPort);
		tcpSocket.setSoTimeout(soTimeout);
		serverSocket = tcpSocket.accept();
		if (serverSocket.isConnected()) {
			System.out.println("Connected to " + serverSocket.getInetAddress().getHostAddress() + ":" + serverSocket.getLocalPort());
		}

		// Start Reading task in parallel thread
		readFromSocket();
	}

	/**
	 * Returns the connection state of the socket. true is returned if socket is
	 * successfully connected and has not been closed
	 */
	public boolean isConnected() {
		if (serverSocket.isConnected() && !serverSocket.isClosed()) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Closes this socket. Once a socket has been closed, it is not available
	 * for further networking use (i.e. can't be reconnected or rebound). A new
	 * socket needs to be created.
	 * 
	 * @throws IOException
	 *             if an I/O error occurs when closing this socket.
	 */
	public void disconnect() throws IOException {
		serverSocket.close();
		tcpSocket.close();
		System.out.println("Connection Closed");
	}

	/**
	 * Returns true if receive queue is not empty
	 */
	@Override
	public boolean hasNextMsg() {
		if (queue.isEmpty()) {
			return false;
		}
		return true;
	}

	/**
	 * Polls the queue for msg, Function will block until msg is polled from the
	 * queue or timeout has occurred. null is returned if no message received
	 * within timeout period
	 * 
	 * @param timeout
	 *            msg timeout
	 * @param timeunit
	 *            timeunit
	 * @return byte[] from queue, null is returned if timeout has occurred
	 * @throws InterruptedException
	 *             if any thread has interrupted the current thread. The
	 *             interrupted status of the current thread is cleared when this
	 *             exception is thrown.
	 */
	public byte[] getNextMsg(long timeout, TimeUnit timeunit) throws InterruptedException {
		boolean isTimeout = false;
		long startTime = System.nanoTime();
		long finishTime;
		long maxAllowedTime = TimeUnit.NANOSECONDS.convert(timeout, timeunit);

		while (!isTimeout) {
			if (hasNextMsg()) {
				return queue.poll();
			}
			finishTime = System.nanoTime();
			if ((finishTime - startTime) > maxAllowedTime) {
				return null;
			}
			// Give system some time to do other things
			Thread.sleep(10);
		}
		return null;
	}

	/**
	 * Returns byte array from the queue, null is returned if queue is empty
	 */
	@Override
	public byte[] getNextMsg() {
		if (hasNextMsg()) {
			return queue.poll();
		}
		return null;
	}

	/**
	 * Send data to client in String format
	 * 
	 * @param data
	 *            data to be sent in String format
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public void sendMsg(String data) throws IOException {
		sendMsg(data.getBytes());
	}

	/**
	 * Send byte array to client
	 * 
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	@Override
	public void sendMsg(byte[] data) throws IOException {
		outToClient = new DataOutputStream(serverSocket.getOutputStream());
		outToClient.write(data);
	}

	/**
	 * Clean receive queue
	 */
	public void cleanQueue() {
		queue.clear();
	}

	private void readFromSocket() {
		final ExecutorService clientProcessingPool = Executors.newFixedThreadPool(10);
		final Runnable serverTask = new Runnable() {
			@Override
			public void run() {
				try {
					clientProcessingPool.submit(new ClientTask(serverSocket, queue, filterList));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		Thread serverThread = new Thread(serverTask);
		serverThread.start();
	}

	public ServerSocket getTcpSocket() {
		return tcpSocket;
	}

	public Socket getConnector() {
		return serverSocket;
	}

	public Queue<byte[]> getQueue() {
		return queue;
	}

	public int getnPort() {
		return nPort;
	}

	public void setnPort(int nPort) {
		this.nPort = nPort;
	}

}

/**
 * Inner Class which acts as receiver thread for incoming data. All Data will be
 * added to the Queue
 * 
 * 
 *
 */
class ClientTask implements Runnable {

	private final Socket connector;
	int read = -1;
	byte[] buffer = new byte[4 * 1024]; // a read buffer of 4KiB
	byte[] readData;
	String redDataText;
	Queue<byte[]> queue;
	volatile List<ConnectableFilter> filterList = null;

	ClientTask(Socket connector, Queue<byte[]> queue) {
		this.connector = connector;
		this.queue = queue;
		this.filterList = null;
	}

	ClientTask(Socket connector, Queue<byte[]> queue, List<ConnectableFilter> filterList) {
		this.connector = connector;
		this.queue = queue;
		this.filterList = filterList;
	}

	@Override
	public void run() {
		try {
			while ((read = connector.getInputStream().read(buffer)) > -1) {
				readData = new byte[read];
				System.arraycopy(buffer, 0, readData, 0, read);
				if (readData.length > 0) {
					applyFilter(readData);
				}
			}
		} catch (Exception e) {
			if (connector.isClosed() && e.getMessage().contains("Socket closed")) {
				// Do nothing because if connector was closed then this
				// exception is as expected
			} else {
				e.printStackTrace();
			}
		}
	}

	private void applyFilter(byte[] readData) throws Exception {
		if (null != filterList && !filterList.isEmpty()) {
			for (ConnectableFilter filter : filterList) {
				if (filter.meetCriteria(readData)) {
					// Do not add to queue if filter match is found
					return;
				}
			}
			queue.add(readData);
		} else {
			queue.add(readData);
		}
	}
}