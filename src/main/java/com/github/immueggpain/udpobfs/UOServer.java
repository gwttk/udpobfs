package com.github.immueggpain.udpobfs;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import com.github.immueggpain.udpobfs.Launcher.ServerSettings;

public class UOServer {

	public void run(ServerSettings settings) {
		try {
			// convert password to aes key
			byte[] bytes = settings.password.getBytes(StandardCharsets.UTF_8);
			byte[] byteKey = new byte[16];
			System.arraycopy(bytes, 0, byteKey, 0, Math.min(byteKey.length, bytes.length));
			SecretKeySpec secretKey = new SecretKeySpec(byteKey, "AES");
			// we use 2 ciphers because we want to support encrypt/decrypt full-duplex
			String transformation = "AES/GCM/PKCS5Padding";
			Cipher encrypter = Cipher.getInstance(transformation);
			Cipher decrypter = Cipher.getInstance(transformation);

			// setup sockets
			InetAddress allbind_addr = InetAddress.getByName("0.0.0.0");
			InetAddress loopback_addr = InetAddress.getByName("127.0.0.1");
			DatagramSocket sclient_s = new DatagramSocket(settings.server_port, allbind_addr);
			DatagramSocket capp_s = new DatagramSocket(0, loopback_addr);

			// start working threads
			TunnelContext contxt = new TunnelContext();
			Thread transfer_c2s_thread = Util.execAsync("transfer_c2s", () -> transfer_c2s(sclient_s, decrypter,
					secretKey, loopback_addr, settings.dest_port, capp_s, contxt));
			Thread transfer_s2c_thread = Util.execAsync("transfer_s2c",
					() -> transfer_s2c(capp_s, encrypter, secretKey, sclient_s, contxt));

			transfer_c2s_thread.join();
			transfer_s2c_thread.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void transfer_c2s(DatagramSocket sclient_s, Cipher decrypter, Key secretKey,
			InetAddress loopback_addr, int dest_port, DatagramSocket capp_s, TunnelContext contxt) {
		try {
			byte[] recvBuf = new byte[4096];
			DatagramPacket p = new DatagramPacket(recvBuf, recvBuf.length);
			while (true) {
				p.setData(recvBuf);
				sclient_s.receive(p);
				contxt.client_sockaddr = p.getSocketAddress();
				byte[] decrypted = Util.decrypt(decrypter, secretKey, p.getData(), p.getOffset(), p.getLength());
				p.setData(decrypted);
				p.setAddress(loopback_addr);
				p.setPort(dest_port);
				capp_s.send(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void transfer_s2c(DatagramSocket capp_s, Cipher encrypter, Key secretKey, DatagramSocket sclient_s,
			TunnelContext contxt) {
		try {
			byte[] recvBuf = new byte[4096];
			DatagramPacket p = new DatagramPacket(recvBuf, recvBuf.length);
			while (true) {
				p.setData(recvBuf);
				capp_s.receive(p);
				if (contxt.client_sockaddr == null)
					continue;
				byte[] encrypted = Util.encrypt(encrypter, secretKey, p.getData(), p.getOffset(), p.getLength());
				p.setData(encrypted);
				p.setSocketAddress(contxt.client_sockaddr);
				sclient_s.send(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static class TunnelContext {
		public SocketAddress client_sockaddr;
	}

}
