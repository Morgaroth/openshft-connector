package pl.morgaroth.lib.open_shift_connector.client;

import java.util.List;

import com.jcraft.jsch.Session;

public interface IPortForwarder {

	public List<IApplicationPortForwarding> getForwardablePorts()
			throws OpenShiftSSHOperationException;

	public Session getSSHSession();

	public List<IApplicationPortForwarding> startPortForwarding()
			throws OpenShiftSSHOperationException;

	public boolean hasSSHSession();

	public List<IApplicationPortForwarding> stopPortForwarding()
			throws OpenShiftSSHOperationException;

	public IApplicationPortForwarding startOnePortForwarding(
			IApplicationPortForwarding port)
			throws OpenShiftSSHOperationException;

	public String getLocalAddress();

	public void setLocalAddress(String localAddress);

	public IApplicationPortForwarding startForwardingOn(int port);
}