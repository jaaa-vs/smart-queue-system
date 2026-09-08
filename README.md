# Smart Queue Management System

A lightweight queue management application for service counters, built with Java, MySQL, and a responsive browser interface. Staff can issue queue tickets, call and serve customers, monitor activity in real time, and display the current queue on a separate screen.

## Highlights

- Generate queue tickets by service and counter
- Call the next customer and update ticket status as customers are served
- Configure the organization, available services, and service windows
- Monitor live queue activity with automatic refresh
- View queue history and basic operational statistics
- Use a dedicated fullscreen display for TVs or HDMI-connected monitors
- Start with a first-run setup dialog and continue managing settings from the web UI

## Technology

- Java 21
- MySQL with JDBC
- NanoHTTPD
- HTML, CSS, and vanilla JavaScript
- Java Swing for the first-run configuration dialog and desktop display support

## Requirements

Install the following before running the application:

- Windows
- Java Development Kit (JDK) 21 or later, including `javac`
- MySQL Server, or XAMPP with MySQL enabled
- MySQL Connector/J placed in the project directory as `mysql-connector-j-9.6.0.jar`

Confirm that Java is available from a Command Prompt:

```bat
java -version
javac -version
```

## Installation

1. Start MySQL from XAMPP or the MySQL service manager.
2. Open phpMyAdmin or a MySQL client.
3. Import [`setup.sql`](setup.sql). The script creates the `queue_system` database, tables, default services, and default windows.
4. Confirm that the MySQL connection settings in [`QueueService.java`](QueueService.java) match your local MySQL installation.
5. Ensure `mysql-connector-j-9.6.0.jar` is in the project root.

## Run the Application

From the project directory, run:

```bat
run.bat
```

The batch file compiles the Java source files and starts the local server. When the server is running, open:

```text
http://localhost:8080/
```

On the first run, complete the setup dialog or use the Settings page to configure the organization, services, and windows.

## Available Pages

| Page | Address | Purpose |
| --- | --- | --- |
| Dashboard | `http://localhost:8080/index.html` | Main application overview |
| Live Queue | `http://localhost:8080/live.html` | Generate tickets and manage active queues |
| Queue History | `http://localhost:8080/history.html` | Review completed and previous tickets |
| Public Display | `http://localhost:8080/display.html` | Show the current ticket and service window |
| API | `http://localhost:8080/api/` | Local application API used by the web interface |

For a public display, open `display.html` on the presentation monitor and switch the browser to fullscreen mode.

## Suggested Demo Flow

1. Start MySQL and launch the application with `run.bat`.
2. Open the dashboard and verify that the organization, services, and windows are configured.
3. Open **Live Queue**, select a service and window, and generate a ticket.
4. Call the next ticket and mark it as served.
5. Open `display.html` in another browser window to show the active ticket.
6. Open **Queue History** to review the completed transaction.

## Project Structure

```text
Smart Queue Management System/
├── *.java                 Java application and web server source
├── setup.sql              Database schema and default data
├── run.bat                Compile and launch script for Windows
├── manifest.json          Web application manifest
└── web/                   Browser interface pages, styles, and scripts
```

## Troubleshooting

**The application cannot connect to MySQL**

- Confirm that MySQL is running.
- Verify the database name, username, password, host, and port in `QueueService.java`.
- Confirm that `setup.sql` was imported successfully.

**The application does not compile**

- Confirm that JDK 21 or later is installed, rather than only a Java runtime.
- Confirm that `mysql-connector-j-9.6.0.jar` is in the project root.
- Run `run.bat` from the project directory so the relative classpath resolves correctly.

**The browser cannot open the application**

- Keep the Command Prompt running while using the application.
- Open `http://localhost:8080/` after the server starts.
- Check whether another application is already using port 8080.

## License

No license has been specified for this project yet.

