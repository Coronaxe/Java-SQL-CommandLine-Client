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

	private static final String version = "0.9a";
	private static final String cpath = "config.ini";
	private static final String info = "Java SQL Client Version " + version
			+ "\n(c) 2015 Viktor Fuchs\n";

	protected SQLDriver sqld;
	private static ConfigManager cm = new ConfigManager(cpath);
	private static Properties config = new Properties();

	private static HashMap<String, String> commandList = new HashMap<String, String>();

	private static void registerCommands() {
		commandList.put("/help", "Display this helptext");
		commandList
				.put("/connect host username password [database]",
						"Connects to new database, specify password as \"null\", if not set. Database is Optional.");
		commandList
				.put("/save",
						"Saves the current connection as configuration (WARNING: PASSWORD IS SAVED AS PLAIN TEXT!)");
		commandList.put("/clear", "Resets and deletes a saved configuration");
		commandList.put("/exit", "Disconnects properly and quits the program");
	}

	private static SQLDriver createConnection(Properties configuration)
			throws SQLException {
		if (config.getProperty("password").equals("null")) {
			config.setProperty("password", "");
		}
		if (config.getProperty("db") == null) {
			config.setProperty("db", "");
		}
		return new SQLDriver(config.getProperty("host"),
				config.getProperty("user"), config.getProperty("password"),
				config.getProperty("db"));

	}

	private static void updateConfig(String[] credentials) {
		config.setProperty("host", credentials[0]);
		config.setProperty("user", credentials[1]);
		config.setProperty("password", credentials[2]);
		if (credentials.length > 3) {
			config.setProperty("db", credentials[3]);
		}
	}

	public static void main(String[] args) {
		System.out.println(info);

		SQLCli scli = new SQLCli();
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		registerCommands();

		try {
			if (args.length < 1) {
				if (cm.configExists()) {
					System.out
							.println("Configuration found! Loading Config...");
					config = cm.loadConfig();
				} else {
					System.out
							.println("Please specify host, username and password like this: host user password [database]\nPlease specify password with \"null\", if no password is set");
					String[] credentials = br.readLine().split(" ");
					if (credentials.length < 3) {
						throw new SQLException("Invalid Credentials");
					}
					updateConfig(credentials);
				}
			}

			scli.sqld = createConnection(config);
			System.out.println("\nEstablishing connection to database "
					+ config.getProperty("user") + "@"
					+ config.getProperty("host") + "...");
		} catch (SQLException e) {
			System.err
					.println("ERROR! SQL Connection could not be established.");
			System.err.println("Cause: " + e.getMessage());
			if (cm.configExists()) {
				System.out
						.println("It may be the cause of a faulty configuration. Do you wish to delete it? Y/N");
				try {
					if (br.readLine().equalsIgnoreCase("Y")) {
						System.out.println("Deleting config...");
						cm.deleteConfig();
					}
				} catch (IOException e1) {
					System.err.println("UNKNOWN IOERR:");
					e1.printStackTrace();
				}
			}
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
					System.out.println(info);
					System.out.println("Command List:");
					for (Entry<String, String> e : commandList.entrySet()) {
						System.out.println(">" + e.getKey() + " : "
								+ e.getValue());
					}
				} else if (cmd.toUpperCase().startsWith("/CONNECT")) {
					try {
						String[] credentials = cmd.substring(
								cmd.indexOf(" ") + 1).split(" ");
						if (credentials.length < 3) {
							throw new SQLException("Invalid Credentials");
						}
						System.out.println("Disconnecting...");
						scli.sqld.disconnect();
						updateConfig(credentials);
						System.out
								.println("\nEstablishing connection to database "
										+ config.getProperty("user")
										+ "@"
										+ config.getProperty("host") + "...");
						scli.sqld = createConnection(config);
					} catch (SQLException sqle) {
						System.err
								.println("ERROR! SQL Connection could not be established.");
						System.err.println("Cause: " + sqle.getMessage());
						System.exit(0);
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
