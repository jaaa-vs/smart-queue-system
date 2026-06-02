# Queue Management System v4.0
## Professional Web and Java UI

## Setup (quick)
```
1. Start MySQL (e.g., XAMPP Control Panel)
2. Import `setup.sql` via phpMyAdmin to create the `queue_system` database
3. Run `run.bat`
4. Open http://localhost:8080/ and complete Settings if needed
```

**Verify**: Settings tab shows organization, services, and windows. Live tab can generate a ticket.

## Demo flow (suggested)
```
1. Start application and confirm Settings are populated
2. On Live tab, select a window and service and generate a ticket
3. Use Call Next / Serve to progress tickets and observe the display
4. Review History for recent tickets
```

## Features
- Real-time refresh (2s)
- Manage services and windows from Settings
- Responsive web UI and Java Swing display
- External fullscreen display for HDMI
- History and basic statistics

## Notes
The system includes a first-run setup dialog and editable Settings to configure organization details, services, and windows without manual DB edits. The Live view includes a concise insights panel for operational context.

## Tech
Java 21, MySQL, NanoHTTPD, Vanilla JS/CSS

## Production
Run `run.bat` and open `display.html` in fullscreen for external displays.

For presentation, follow the demo flow above.

