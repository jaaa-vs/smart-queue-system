import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.toIntExact;

/**
 * Embedded HTTP API Server for QueueService.
 * Runs on http://localhost:8080/api/*
 * Serves web/ static files at root.
 */
public class QueueApiServer {
    private static final int PORT = 8080;
    private HttpServer server;

    public QueueApiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/", new ApiHandler());
        server.createContext("/", new StaticHandler());
        server.setExecutor(null);
        System.out.println("Queue API Server ready at http://localhost:" + PORT + "/");
        System.out.println("Static web files at http://localhost:" + PORT + "/index.html");
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
    }

    static class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String response = "";
            int status = 200;

            try {
                QueueService service = QueueService.getInstance();
                if (!service.isConnected()) {
                    response = "{\"error\":\"DB Offline\"}";
                    status = 503;
                } else {
                    switch (path) {
                        case "/api/waiting":
                            response = jsonListDetailed(service.getWaitingQueueDetailed());
                            break;
                        case "/api/calling":
                            response = jsonListDetailed(service.getCallingQueueDetailed());
                            break;
                        case "/api/nextbatch":
                            response = jsonListDetailed(service.getNextBatchDetailed(5));
                            break;
                        case "/api/history":
                            response = jsonHistory(service.getHistory());
                            break;
                        case "/api/stats":
                            response = jsonStats(service.getStats());
                            break;
                        case "/api/insights":
                            response = jsonInsights(service.getQueueInsights());
                            break;
                        case "/api/status":
                            response = service.isConnected() ? "{\"connected\":true}" : "{\"connected\":false}";
                            break;
                        case "/api/config":
                            if (method.equals("GET") || method.equals("POST")) {
                                response = jsonConfig(ConfigService.getInstance().getFullConfig());
                            } else if (method.equals("PUT")) {
                                // Parse JSON config and save
                                try (InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                                     BufferedReader br = new BufferedReader(isr)) {
                                    String jsonStr = br.lines().collect(Collectors.joining("\n"));
                                    ConfigService cfg = ConfigService.getInstance();
                                    Map<String, Object> fullConfig = parseConfigJson(jsonStr);
                                    
                                    cfg.loadConfig(); // Ensure fresh data before potential saves
                                    
                                    if (fullConfig != null) {
                                        // Save organization
                                        @SuppressWarnings("unchecked")
                                        Map<String, String> org = (Map<String, String>) fullConfig.get("organization");
                                        if (org != null) {
                                            cfg.saveOrgSettings(
                                                org.getOrDefault("org_name", ""),
                                                org.getOrDefault("address", ""),
                                                org.getOrDefault("tagline", "")
                                            );
                                        }
                                        
                                        // Save services
                                        @SuppressWarnings("unchecked")
                                        List<Map<String, String>> services = (List<Map<String, String>>) fullConfig.get("services");
                                        if (services != null) {
                                            cfg.saveServices(services);
                                        }
                                        
                                        // Save windows
                                        @SuppressWarnings("unchecked")
                                        List<Map<String, String>> windows = (List<Map<String, String>>) fullConfig.get("windows");
                                        if (windows != null) {
                                            cfg.saveWindows(windows);
                                        }
                                        
                                        response = "{\"status\":\"Configuration saved successfully\"}";                                    
                                    } else {
                                        response = "{\"error\":\"Invalid config JSON format\"}";
                                        status = 400;
                                    }
                                } catch (Exception e) {
                                    response = "{\"error\":\"Parse failed: " + e.getMessage() + "\"}";
                                    status = 400;
                                }
                            }
                            break;
                        default:
                            if (method.equals("POST")) {
                                switch (path) {
                                    case "/api/generate":
                                        String serviceParam = exchange.getRequestHeaders().getFirst("service");
                                        String windowParam = exchange.getRequestHeaders().getFirst("window");
                                        if (serviceParam == null || windowParam == null) {
                                            response = "{\"error\":\"service and window required\"}";
                                            status = 400;
                                        } else {
                                            service.generateQueueNum(serviceParam, Integer.parseInt(windowParam));
                                            status = 204;
                                        }
                                        break;
                                    case "/api/callnext":
                                        String called = service.callNext();
                                        if (called != null) response = "{\"called\":\"" + called + "\"}";
                                        else status = 404;
                                        break;
                                    case "/api/reset":
                                        service.resetQueue();
                                        status = 204;
                                        break;
                                    default:
                                        if (path.startsWith("/api/serve/")) {
                                            String num = path.substring("/api/serve/".length());
                                            if (service.serveCurrent(num)) status = 204;
                                            else status = 404;
                                        } else {
                                            status = 404;
                                        }
                                }
                            } else {
                                status = 405;
                            }
                    }
                }
            } catch (Exception e) {
                response = "{\"error\":\"" + e.getMessage() + "\"}";
                status = 500;
            }

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            File file = new File("web" + path);
            String contentType = guessContentType(path);

            if (file.exists() && file.isFile()) {
                long length = file.length();
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, length);
                try (InputStream is = new FileInputStream(file); OutputStream os = exchange.getResponseBody()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            } else {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
            }
        }
    }

    private static String jsonList(List<String> list) {
        return "{\"data\":" + list.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",", "[", "]")) + "}";
    }

    private static String jsonListDetailed(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"data\":[");
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> row = list.get(i);
            sb.append("{");
            sb.append("\"num\":\"").append(escapeJson(String.valueOf(row.getOrDefault("num", "")))).append("\",");
            sb.append("\"service\":\"").append(escapeJson(String.valueOf(row.getOrDefault("service", "")))).append("\",");
            sb.append("\"window\":").append(String.valueOf(row.getOrDefault("window", 0)));
            sb.append("}");
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String jsonHistory(List<Object[]> history) {
        StringBuilder sb = new StringBuilder("{\"data\":[");
        for (int i = 0; i < history.size(); i++) {
            Object[] row = history.get(i);
            sb.append("{\"num\":\"").append(row[0])
              .append("\",\"gen\":\"").append(row[1])
              .append("\",\"called\":\"").append(row[2])
              .append("\",\"status\":\"").append(row[3]).append("\"}");
            if (i < history.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String jsonStats(Map<String, Integer> stats) {
        return "{\"waiting\":" + stats.getOrDefault("waiting", 0) +
               ",\"nextBatch\":" + stats.getOrDefault("nextBatch", 0) +
               ",\"servedToday\":" + stats.getOrDefault("servedToday", 0) + "}";
    }

    @SuppressWarnings("unchecked")
    private static String jsonInsights(Map<String, Object> insights) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"throughputPerHour\":").append(String.valueOf(insights.getOrDefault("throughputPerHour", 0))).append(",");
        sb.append("\"slotSeconds\":").append(String.valueOf(insights.getOrDefault("slotSeconds", 120))).append(",");
        sb.append("\"avgWaitSeconds\":").append(String.valueOf(insights.getOrDefault("avgWaitSeconds", 0))).append(",");
        sb.append("\"waitingCount\":").append(String.valueOf(insights.getOrDefault("waitingCount", 0))).append(",");
        sb.append("\"nextBatchCount\":").append(String.valueOf(insights.getOrDefault("nextBatchCount", 0))).append(",");
        sb.append("\"servedToday\":").append(String.valueOf(insights.getOrDefault("servedToday", 0))).append(",");
        sb.append("\"recommendationLevel\":\"").append(escapeJson(String.valueOf(insights.getOrDefault("recommendationLevel", "low")))).append("\",");
        sb.append("\"recommendation\":\"").append(escapeJson(String.valueOf(insights.getOrDefault("recommendation", "Flow is healthy.")))).append("\",");

        sb.append("\"waitEstimates\":[");
        List<Map<String, Object>> waits = (List<Map<String, Object>>) insights.get("waitEstimates");
        if (waits != null) {
            for (int i = 0; i < waits.size(); i++) {
                Map<String, Object> row = waits.get(i);
                sb.append("{");
                sb.append("\"num\":\"").append(escapeJson(String.valueOf(row.getOrDefault("num", "")))).append("\",");
                sb.append("\"etaMinutes\":").append(String.valueOf(row.getOrDefault("etaMinutes", 0))).append(",");
                sb.append("\"etaLabel\":\"").append(escapeJson(String.valueOf(row.getOrDefault("etaLabel", "")))).append("\"");
                sb.append("}");
                if (i < waits.size() - 1) sb.append(",");
            }
        }
        sb.append("]}");

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String jsonConfig(Map<String, Object> config) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        // Organization
        Map<String, String> org = (Map<String, String>) config.get("organization");
        sb.append("\"organization\":{");
        if (org != null) {
            sb.append("\"org_name\":\"").append(escapeJson(org.getOrDefault("org_name", ""))).append("\",");
            sb.append("\"address\":\"").append(escapeJson(org.getOrDefault("address", ""))).append("\",");
            sb.append("\"tagline\":\"").append(escapeJson(org.getOrDefault("tagline", ""))).append("\"");
        } else {
            sb.append("\"org_name\":\"Smart Queue Management System\",\"address\":\"\",\"tagline\":\"\"");
        }
        sb.append("},");
        // Services
        sb.append("\"services\":[");
        List<Map<String, String>> services = (List<Map<String, String>>) config.get("services");
        if (services != null) {
            for (int i = 0; i < services.size(); i++) {
                Map<String, String> s = services.get(i);
                sb.append("{");
                sb.append("\"id\":\"").append(s.getOrDefault("id", "")).append("\",");
                sb.append("\"service_code\":\"").append(escapeJson(s.getOrDefault("service_code", ""))).append("\",");
                sb.append("\"service_name\":\"").append(escapeJson(s.getOrDefault("service_name", ""))).append("\",");
                sb.append("\"description\":\"").append(escapeJson(s.getOrDefault("description", ""))).append("\"");
                sb.append("}");
                if (i < services.size() - 1) sb.append(",");
            }
        }
        sb.append("],");
        // Windows
        sb.append("\"windows\":[");
        List<Map<String, String>> windows = (List<Map<String, String>>) config.get("windows");
        if (windows != null) {
            for (int i = 0; i < windows.size(); i++) {
                Map<String, String> w = windows.get(i);
                sb.append("{");
                sb.append("\"id\":\"").append(w.getOrDefault("id", "")).append("\",");
                sb.append("\"window_number\":\"").append(w.getOrDefault("window_number", "")).append("\",");
                sb.append("\"window_name\":\"").append(escapeJson(w.getOrDefault("window_name", ""))).append("\",");
                sb.append("\"description\":\"").append(escapeJson(w.getOrDefault("description", ""))).append("\",");
                sb.append("\"service_ids\":\"").append(escapeJson(w.getOrDefault("service_ids", ""))).append("\"");
                sb.append("}");
                if (i < windows.size() - 1) sb.append(",");
            }
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String guessContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        return "application/octet-stream";
    }

    /** Parse config JSON for PUT saves */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseConfigJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
        
        Map<String, Object> config = new LinkedHashMap<>();
        
        try {
            // Simple parser for expected format: {"organization":{...},"services":[...],"windows":[...]}
            
            // Parse organization object
            String orgStr = extractJsonObject(jsonStr, "organization");
            if (orgStr != null) {
                Map<String, String> org = parseSimpleMap(orgStr);
                config.put("organization", org);
            }
            
            // Parse services array
            String svcsStr = extractJsonArray(jsonStr, "services");
            if (svcsStr != null) {
                List<Map<String, String>> svcs = parseObjectArray(svcsStr);
                config.put("services", svcs);
            }
            
            // Parse windows array
            String winsStr = extractJsonArray(jsonStr, "windows");
            if (winsStr != null) {
                List<Map<String, String>> wins = parseObjectArray(winsStr);
                config.put("windows", wins);
            }
            
            return config;
        } catch (Exception e) {
            System.err.println("JSON parse error: " + e.getMessage());
            return null;
        }
    }

    private static String extractJsonObject(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIndex = json.indexOf(search);
        if (keyIndex < 0) return null;

        int colonIndex = json.indexOf(':', keyIndex + search.length());
        if (colonIndex < 0) return null;

        int start = skipWhitespace(json, colonIndex + 1);
        if (start >= json.length() || json.charAt(start) != '{') return null;

        int end = findMatchingBracket(json, start, '{', '}');
        if (end < 0) return null;
        return json.substring(start, end + 1);
    }

    private static String extractJsonArray(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIndex = json.indexOf(search);
        if (keyIndex < 0) return null;

        int colonIndex = json.indexOf(':', keyIndex + search.length());
        if (colonIndex < 0) return null;

        int start = skipWhitespace(json, colonIndex + 1);
        if (start >= json.length() || json.charAt(start) != '[') return null;

        int end = findMatchingBracket(json, start, '[', ']');
        if (end < 0) return null;
        return json.substring(start, end + 1);
    }

    private static Map<String, String> parseSimpleMap(String jsonObj) {
        Map<String, String> map = new LinkedHashMap<>();
        if (jsonObj == null) return map;

        String clean = jsonObj.trim();
        if (clean.startsWith("{")) clean = clean.substring(1);
        if (clean.endsWith("}")) clean = clean.substring(0, clean.length() - 1);
        clean = clean.trim();
        if (clean.isEmpty()) return map;

        List<String> pairs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                current.append(c);
                escaped = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
                continue;
            }
            if (c == ',' && !inQuotes) {
                pairs.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) pairs.add(current.toString());

        for (String pair : pairs) {
            pair = pair.trim();
            if (pair.isEmpty()) continue;
            
            // Find key: value
            int colon = -1;
            inQuotes = false;
            escaped = false;
            for (int i = 0; i < pair.length(); i++) {
                char c = pair.charAt(i);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == '"') {
                    inQuotes = !inQuotes;
                    continue;
                }
                if (c == ':' && !inQuotes) {
                    colon = i;
                    break;
                }
            }
            if (colon > 0) {
                String key = pair.substring(0, colon).trim().replaceAll("^\"|\"$", "");
                String value = pair.substring(colon + 1).trim().replaceAll("^\"|\"$", "").replace("\\\\", "\\");
                map.put(key, value);
            }
        }
        return map;
    }

    private static List<Map<String, String>> parseObjectArray(String jsonArray) {
        List<Map<String, String>> list = new ArrayList<>();
        if (jsonArray == null) return list;

        String clean = jsonArray.trim();
        if (!clean.startsWith("[") || !clean.endsWith("]")) return list;
        if ("[]".equals(clean)) return list;

        int i = 1;
        while (i < clean.length() - 1) {
            i = skipWhitespace(clean, i);
            if (i >= clean.length() - 1) break;

            if (clean.charAt(i) == ',') {
                i++;
                continue;
            }
            if (clean.charAt(i) != '{') {
                i++;
                continue;
            }

            int end = findMatchingBracket(clean, i, '{', '}');
            if (end < 0) break;

            Map<String, String> map = parseSimpleMap(clean.substring(i, end + 1));
            if (!map.isEmpty()) list.add(map);

            i = end + 1;
        }
        return list;
    }

    private static int skipWhitespace(String s, int index) {
        int i = index;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int findMatchingBracket(String s, int start, char open, char close) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}

