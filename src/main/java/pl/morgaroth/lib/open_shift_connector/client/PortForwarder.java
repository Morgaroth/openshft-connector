package pl.morgaroth.lib.open_shift_connector.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class PortForwarder implements IPortForwarder {

	
	protected enum SshStreams {
		EXT_INPUT {
			protected InputStream getInputStream(Channel channel) throws IOException {
				return channel.getExtInputStream(); 
			}

		}, INPUT {
			protected InputStream getInputStream(Channel channel) throws IOException {
				return channel.getInputStream(); 
			}
		};
		
		public List<String> getLines(Channel channel) throws IOException {
			BufferedReader reader = new BufferedReader(new InputStreamReader(getInputStream(channel)));
			List<String> lines = new ArrayList<String>();
			String line = null;
			// Read File Line By Line
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
			return lines;
		}
		
		protected abstract InputStream getInputStream(Channel channel) throws IOException;

	}
	
	private String localAddress = "127.0.0.1";
	private static final Pattern REGEX_FORWARDED_PORT = Pattern
			.compile("([^ ]+) -> ([^:]+):(\\d+)");
	private static final int MIN_PORT_NUMBER = 1000;
	private static final int MAX_PORT_NUMBER = 60000;
	private List<IApplicationPortForwarding> ports;
	private Session session;
	private String name;

	public PortForwarder(Session session) {
		this.session = session;
	}

	public String getName() {
		return name;
	}
	
	/* (non-Javadoc)
	 * @see com.openshift.internal.client.IPortForwarder#getForwardablePorts()
	 */
	@Override
	public List<IApplicationPortForwarding> getForwardablePorts() throws OpenShiftSSHOperationException {
		if (ports == null) {
			this.ports = loadPorts();
		}
		return ports;
	}

	private List<IApplicationPortForwarding> loadPorts()
			throws OpenShiftSSHOperationException {
		this.ports = new ArrayList<IApplicationPortForwarding>();
		List<String> lines = sshExecCmd("rhc-list-ports", SshStreams.EXT_INPUT);
		for (String line : lines) {
			ApplicationPortForwarding port = extractForwardablePortFrom(line);
			if (port != null) {
				ports.add(port);
			}
		}
		return ports;
	}

	private ApplicationPortForwarding extractForwardablePortFrom(
			final String portValue) {
		Matcher matcher = REGEX_FORWARDED_PORT.matcher(portValue);
		if (!matcher.find() || matcher.groupCount() != 3) {
			return null;
		}
		try {
			final String name = matcher.group(1);
			final String host = matcher.group(2);
			final int remotePort = Integer.parseInt(matcher.group(3));
			return new ApplicationPortForwarding(name, host, remotePort);
		} catch (NumberFormatException e) {
			throw new OpenShiftSSHOperationException(e,
					"Couild not determine forwarded port in application {0}",
					getName());
		}
	}

	/* (non-Javadoc)
	 * @see com.openshift.internal.client.IPortForwarder#getSSHSession()
	 */
	@Override
	public Session getSSHSession() {
		return this.session;
	}

	protected List<String> sshExecCmd(final String command,
			final SshStreams sshStream) throws OpenShiftSSHOperationException {
		final Session session = getSSHSession();
		if (session == null) {
			throw new OpenShiftSSHOperationException(
					"No SSH session available for application ''{0}''",
					this.getName());
		}
		Channel channel = null;
		BufferedReader reader = null;
		try {
			session.openChannel("exec");
			channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(command);
			channel.connect();
			return sshStream.getLines(channel);
		} catch (JSchException e) {
			throw new OpenShiftSSHOperationException(e,
					"Failed to list forwardable ports for application \"{0}\"",
					this.getName());
		} catch (IOException e) {
			throw new OpenShiftSSHOperationException(e,
					"Failed to list forwardable ports for application \"{0}\"",
					this.getName());
		} finally {

			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (channel != null && channel.isConnected()) {
				channel.disconnect();
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.openshift.internal.client.IPortForwarder#startPortForwarding()
	 */
	@Override
	public List<IApplicationPortForwarding> startPortForwarding()
			throws OpenShiftSSHOperationException {
		if (!hasSSHSession()) {
			throw new OpenShiftSSHOperationException(
					"SSH session for application \"{0}\" is closed or null. Cannot start port forwarding",
					getName());
		}
		for (IApplicationPortForwarding port : getForwardablePorts()) {
			try {
				port.setLocalAddress(getLocalAddress());
				int local = getFirstFreePort(port.getRemotePort());
				if (local < 0) {
					throw new RuntimeException("not enaught free ports");
				}
				port.setLocalPort(local);
				port.start(session);
			} catch (OpenShiftSSHOperationException oss) {
				/*
				 * ignore for now FIXME: should store this error on the forward
				 * to let user know why it could not start/stop
				 */
			}
		}
		return ports;
	}

	@Override
	public String getLocalAddress() {
		return localAddress;
	}
	
	@Override
	public void setLocalAddress(String localAddress) {
		this.localAddress=localAddress;
	}
	
	@Override
	public IApplicationPortForwarding startOnePortForwarding(IApplicationPortForwarding port)
			throws OpenShiftSSHOperationException {
		if (!hasSSHSession()) {
			throw new OpenShiftSSHOperationException(
					"SSH session for application \"{0}\" is closed or null. Cannot start port forwarding",
					getName());
		}
		
			try {
				port.setLocalAddress(getLocalAddress());
				int local = getFirstFreePort(port.getRemotePort());
				if (local < 0) {
					throw new RuntimeException("not enaught free ports");
				}
				port.setLocalPort(local);
				port.start(session);
			} catch (OpenShiftSSHOperationException oss) {
				/*
				 * ignore for now FIXME: should store this error on the forward
				 * to let user know why it could not start/stop
				 */
			}
		
		return port;
	}
	
	@Override
	public IApplicationPortForwarding startForwardingOn(int port){
		List<IApplicationPortForwarding> forwardablePorts = getForwardablePorts();
		IApplicationPortForwarding porta = null;
		Iterator<IApplicationPortForwarding> it = forwardablePorts.iterator();
		while(it.hasNext()){
			IApplicationPortForwarding next = it.next();
			if (next.getRemotePort()==4447){
				porta=next;
				break;
			}
		}
		if (porta!=null) {
			return startOnePortForwarding(porta);
		}
		throw new IllegalArgumentException("incalid port");
	}

	/* (non-Javadoc)
	 * @see com.openshift.internal.client.IPortForwarder#hasSSHSession()
	 */
	@Override
	public boolean hasSSHSession() {
		return this.session != null && this.session.isConnected();
	}

	
	
	private static int getFirstFreePort(int startingPort) {
		int port = getFreeAvaliblePortStartingFrom(startingPort,
				MAX_PORT_NUMBER);
		if (port < 0) {
			port = getFreeAvaliblePortStartingFrom(MIN_PORT_NUMBER,
					startingPort);
		}
		return port;
	}

	private static int getFreeAvaliblePortStartingFrom(int startingPort, int to) {
		for (; startingPort < to; ++startingPort) {
			if (available(startingPort)) {
				return startingPort;
			}
		}
		return -1;
	}

	private static boolean available(int port) {
		if (port < MIN_PORT_NUMBER || port > MAX_PORT_NUMBER) {
			throw new IllegalArgumentException("Invalid start port: " + port);
		}

		ServerSocket ss = null;
		DatagramSocket ds = null;
		try {
			ss = new ServerSocket(port);
			ss.setReuseAddress(true);
			ds = new DatagramSocket(port);
			ds.setReuseAddress(true);
			return true;
		} catch (IOException e) {
		} finally {
			if (ds != null) {
				ds.close();
			}

			if (ss != null) {
				try {
					ss.close();
				} catch (IOException e) {
					/* should not be thrown */
				}
			}
		}

		return false;
	}

	/* (non-Javadoc)
	 * @see com.openshift.internal.client.IPortForwarder#stopPortForwarding()
	 */
	@Override
	public List<IApplicationPortForwarding> stopPortForwarding()
			throws OpenShiftSSHOperationException {
		for (IApplicationPortForwarding port : ports) {
			try {
				port.stop(session);
			} catch (OpenShiftSSHOperationException oss) {
				/*
				 * ignore for now should store this error on the forward to let
				 * user know why it could not start/stop
				 */
			}
		}
		// make sure port forwarding is stopped by closing session...
		session.disconnect();
		return ports;
	}

}
