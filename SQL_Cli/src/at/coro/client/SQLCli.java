package at.coro.client;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;

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

	private static TreeMap<String, String> commandList = new TreeMap<String, String>();

	private static void registerCommands() {
		commandList.put("/help", "Displays this helptext");
		commandList
				.put("/connect host username password [database]",
						"Connects to new database, specify password as \"null\", if not set. Database is optional.");
		commandList
				.put("/save",
						"Saves the current connection as configuration (WARNING: PASSWORD IS SAVED AS PLAIN TEXT!)");
		commandList
				.put("/script path_to_script [verbose]",
						"Runs the specified script. If verbose is true, outputs the script as it is run. Optional.");
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
		System.out.println("\nEstablishing connection to database "
				+ config.getProperty("user") + "@" + config.getProperty("host")
				+ "...");
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

	private static void listResults(ResultSet rs) throws SQLException {
		int rc = 0;
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
		} catch (SQLException e) {
			System.err
					.println("ERROR! SQL Connection could not be established.");
			System.err.println("Cause: " + e.getMessage());
			if (cm.configExists()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e2) {
					e2.printStackTrace();
				}
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
						scli.sqld = createConnection(config);
					} catch (SQLException sqle) {
						System.err
								.println("ERROR! SQL Connection could not be established.");
						System.err.println("Cause: " + sqle.getMessage());
						System.exit(0);
					}
				} else if (cmd.toUpperCase().startsWith("/SCRIPT")) {
					String sCurrentLine;
					String concate = "";
					String[] parms = cmd.substring(cmd.indexOf(" ") + 1).split(
							" ");
					BufferedReader fbr = new BufferedReader(new FileReader(
							parms[0]));
					System.out.println("Executing Script " + parms[0] + "...");
					while ((sCurrentLine = fbr.readLine()) != null) {
						if (!sCurrentLine.startsWith("--")) {
							concate += sCurrentLine;
							if (sCurrentLine.endsWith(";")) {
								if (parms.length > 1
										&& parms[1].equalsIgnoreCase("true")) {
									System.out.println(concate);
								}
								ResultSet trs = scli.sqld.autoExecute(concate);
								concate = "";
								if (trs != null) {
									listResults(trs);
								}
							}
						}
					}
					fbr.close();
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
					ResultSet rs = scli.sqld.executeQuery(cmd);
					listResults(rs);
				} else {
					scli.sqld.execute(cmd);
				}
			} catch (IOException e) {
				System.err.println("IO exception!");
				System.err.println("Cause: " + e.getMessage());
			} catch (SQLException e) {
				System.err
						.println("There was an exception. Please check your SQL Syntax!");
				System.err.println("Cause: " + e.getMessage());
				// e.printStackTrace();
			} finally {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
				}
			}
			System.out.println();
		}

	}
}
