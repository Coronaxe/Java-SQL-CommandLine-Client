package at.coro.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;

import at.coro.sql.SQLDriver;
import at.coro.utils.ConfigManager;

public class SQLCli {

	private static final String version = "0.8b";
	private static final String cpath = "config.ini";

	protected SQLDriver sqld;
	private static ConfigManager cm = new ConfigManager(cpath);
	private static Properties config = new Properties();

	private static HashMap<String, String> commandList = new HashMap<String, String>();

	private static void registerCommands() {
		commandList.put("/help", "Display this helptext");
		commandList
				.put("/save",
						"Saves the current connection as configuration (WARNING: PASSWORD IS SAVED AS PLAIN TEXT!)");
		commandList.put("/clear", "Resets and deletes a saved configuration");
		commandList.put("/exit", "Disconnects properly and quits the program");
	}

	public static void main(String[] args) {
		String[] credentials = args;
		System.out.println("Java SQL Client Version " + version
				+ "\n(c) 2015 Viktor Fuchs");

		SQLCli scli = new SQLCli();
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		registerCommands();

		try {
			if (args.length < 1) {
				if (cm.configExists()) {
					config = cm.loadConfig();
				} else {
					System.out
							.println("Please specify host, username and password like this: host user password [database]\nPlease specify password with \"null\", if no password is set");
					credentials = br.readLine().split(" ");
					config.setProperty("host", credentials[0]);
					config.setProperty("user", credentials[1]);
					config.setProperty("password", credentials[2]);
					if (credentials.length > 3) {
						config.setProperty("db", credentials[3]);
					}
				}
			}

			if (config.getProperty("password").equals("null")) {
				config.setProperty("password", "");
			}
			if (config.getProperty("db") == null) {
				config.setProperty("db", "");
			}
			scli.sqld = new SQLDriver(config.getProperty("host"),
					config.getProperty("user"), config.getProperty("password"),
					config.getProperty("db"));
			System.out.println("\nEstablishing connection to database "
					+ config.getProperty("user") + "@"
					+ config.getProperty("host") + "...");
		} catch (SQLException e) {
			System.err
					.println("ERROR! SQL Connection could not be established.");
			System.err.println("Cause: " + e.getMessage());
			// e.printStackTrace();
			System.exit(0);
		} catch (IOException e) {
			e.printStackTrace();
		}
		while (true) {
			try {
				System.out.print("Database Selected: ");
				ResultSet useddb = scli.sqld.getDatabase();
				useddb.first();
				if (useddb.getString(1) == "null") {
					System.out.println("None");
				} else {
					config.setProperty("db", useddb.getString(1));
					System.out.println(useddb.getString(1));
				}

				System.out
						.println("Waiting for input. /help to see command list.");
				String cmd;
				do {
					System.out.print(">");
					cmd = br.readLine();
				} while (cmd.isEmpty());
				// System.out.println("Command is: " + cmd);
				System.out.println();
				if (cmd.toUpperCase().startsWith("/HELP")) {
					System.out.println("Command List:");
					for (Entry<String, String> e : commandList.entrySet()) {
						System.out.println(">" + e.getKey() + " : "
								+ e.getValue());
					}
				} else if (cmd.toUpperCase().startsWith("/SAVE")) {
					System.out.println("Creating config...");
					System.out.println("Saving config...");
					cm.saveConfig(config);
					System.out.println("Config saved!");
				} else if (cmd.toUpperCase().startsWith("/CLEAR")) {
					if (cm.configExists()) {
						cm.deleteConfig();
						System.out.println("Config deleted!");
					} else {
						System.out.println("No config found!");
					}
				} else if (cmd.toUpperCase().startsWith("/EXIT")) {
					System.out.println("Exiting...");
					scli.sqld.disconnect();
					System.exit(0);
				} else if (cmd.toUpperCase().startsWith("SELECT")
						|| cmd.toUpperCase().startsWith("SHOW")) {
					int rc = 0;
					ResultSet rs = scli.sqld.executeQuery(cmd);
					for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
						System.out.print(rs.getMetaData().getColumnLabel(i));
						if (i < rs.getMetaData().getColumnCount()) {
							System.out.print("\t|\t");
						}
					}
					System.out.println();
					while (rs.next()) {
						rc++;
						for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
							System.out.print(rs.getObject(i));
							if (i < rs.getMetaData().getColumnCount()) {
								System.out.print("\t|\t");
							}
						}
						System.out.println();
					}
					System.out.println(rc + " rows selected");
				} else {
					scli.sqld.execute(cmd);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (SQLException e) {
				System.err
						.println("There was an exception. Please check your SQL Syntax!");
				System.err.println("Cause: " + e.getMessage());
				// e.printStackTrace();
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
				}
			}
			System.out.println();
		}

	}
}
