package pl.morgaroth.lib.open_shift_connector.client;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class Forwarder {

	private Session sessio;
	private IPortForwarder forwarder;
	private Thread worker;
	private boolean init = false;
	private boolean forward = false;
	private boolean end = false;
	private Lock lock = new ReentrantLock();

	public static void main(String[] args) throws JSchException {
		Forwarder f = new Forwarder();
		f.init();
		f.runForward();
		try {
			TimeUnit.MINUTES.sleep(5);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		f.stop();
	}

	public Forwarder() throws JSchException {

		worker = new Thread(new Runnable() {

			@Override
			public void run() {
				while (!Thread.interrupted()) {
					lock.lock();
					try {
						if (init) {
							try {
								inits();
							} catch (JSchException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							init = false;
						}
						if (forward) {

							forward();
							forward = false;
						}
						if (end) {
							nonForward();
							break;
						}
					} finally {
						lock.unlock();
					}
				}
			}
		});
		worker.start();

	}

	protected void inits() throws JSchException {
		System.setProperty("java.net.preferIPv4Stack", "true");

		JSch jsch = new JSch();
		jsch.addIdentity("/home/mateusz/.ssh/id_rsa");
		sessio = jsch.getSession("52bda8e4e0b8cd1e430001cf",
				"learn-morgaroth.rhcloud.com", 22);
		java.util.Properties config = new java.util.Properties();
		config.put("StrictHostKeyChecking", "no");

		sessio.setConfig(config);
		sessio.connect();
	}

	protected void forward() {
		forwarder = new PortForwarder(sessio);
		forwarder.startPortForwarding();
		for (IApplicationPortForwarding port : forwarder.getForwardablePorts()) {
			System.out.println(port.getLocalAddress() + ":"
					+ port.getLocalPort() + "=>" + port.getRemoteAddress()
					+ ":" + port.getRemotePort());
		}
	}

	protected void nonForward() {
		forwarder.stopPortForwarding();
	}

	public void init() {
		lock.lock();
		init = true;
		lock.unlock();
	}

	public void runForward() {
		lock.lock();
		forward = true;
		lock.unlock();
	}

	public void stop() {
		lock.lock();
		end = true;
		lock.unlock();
	}

	public Collection<IApplicationPortForwarding> getForwardedPorts() {
		lock.lock();
		try {
			return forwarder.getForwardablePorts();
		} finally {
			lock.unlock();
		}
	}
}
