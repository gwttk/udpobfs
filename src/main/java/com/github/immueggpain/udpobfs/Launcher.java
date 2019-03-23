package com.github.immueggpain.udpobfs;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Launcher {

	private static final String VERSTR = "0.1.0";

	public static class ClientSettings {
		public String password;
		public String server_ip;
		public int server_port;
		public int client_port;
	}

	public static class ServerSettings {
		public String password;
		public int server_port;
		public int dest_port;
	}

	public static void main(String[] args) {
		// in laucher, we dont use log file, just print to console
		// cuz it's all about process input args
		try {
			new Launcher().run(args);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("use -h to see help");
		}
	}

	private void run(String[] args) throws ParseException {
		// option long names
		String help = "help";
		String mode = "mode";
		String password = "password";
		String client_port = "client_port";
		String server_ip = "server_ip";
		String server_port = "server_port";
		String dest_port = "dest_port";

		// define options
		Options options = new Options();
		options.addOption("h", help, false, "print help then exit");
		options.addOption(Option.builder("m").longOpt(mode).hasArg().desc("mode, server or client, default is client")
				.argName("MODE").build());
		options.addOption(Option.builder("w").longOpt(password).hasArg()
				.desc("password of server or client, must be same, recommend 64 bytes").argName("PASSWORD").build());
		options.addOption(
				Option.builder("c").longOpt(client_port).hasArg().desc("client port").argName("PORT").build());
		options.addOption(Option.builder("s").longOpt(server_ip).hasArg().desc("server ip").argName("IP").build());
		options.addOption(
				Option.builder("p").longOpt(server_port).hasArg().desc("server port").argName("PORT").build());
		options.addOption(
				Option.builder("d").longOpt(dest_port).hasArg().desc("destination port").argName("PORT").build());

		// parse from cmd args
		DefaultParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);

		// first let's check if it's help
		if (cmd.hasOption(help)) {
			String header = "";
			String footer = "\nPlease report issues at https://github.com/Immueggpain/udpobfs/issues";

			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("java -jar udpobfs-" + VERSTR + ".jar", header, options, footer, true);
			return;
		}

		// server or client
		if (cmd.hasOption(mode) && cmd.getOptionValue(mode).equals("server")) {
			// run as server
			ServerSettings settings = new ServerSettings();
			settings.password = cmd.getOptionValue(password);
			settings.server_port = Integer.parseInt(cmd.getOptionValue(server_port));
			settings.dest_port = Integer.parseInt(cmd.getOptionValue(dest_port));
			try {
				new UOServer().run(settings);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return;
		} else {
			// run as client
			ClientSettings settings = new ClientSettings();
			settings.server_ip = cmd.getOptionValue(server_ip);
			settings.server_port = Integer.parseInt(cmd.getOptionValue(server_port));
			settings.password = cmd.getOptionValue(password);
			settings.client_port = Integer.parseInt(cmd.getOptionValue(client_port));
			try {
				new UOClient().run(settings);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
