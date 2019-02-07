package rift;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

public class AccessTransformerUpdater {

    private static final Pattern visibilityPattern = Pattern.compile("(public|protected|default|private)(-f)?");

    public static class ClassMapping {
        public final Set<String> constructors = new HashSet<>();
        public final Map<String, String> methods = new HashMap<>();
        public final Map<String, String> fields = new HashMap<>();
        public final String notchName, mcpName;

        public ClassMapping(String notchName, String mcpName) {
            this.notchName = notchName;
            this.mcpName = mcpName;
        }
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
        Path atPath = Paths.get("access_transformations.at");
        if (!Files.exists(atPath)) {
            System.err.println("access_transformations.at must be in current run directory");
            return;
        }
        List<String> lines = Files.readAllLines(atPath);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<Map<String, ClassMapping>> srgTask = executor.submit(() -> {
            HashSet<String> set = new HashSet<>();
            set.add("config/joined.tsrg");
            set.add("config/constructors.txt");
            Map<String, String> files = download("http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/1.13/mcp_config-1.13.zip", set);
            try (BufferedReader contents = new BufferedReader(new StringReader(files.get("config/joined.tsrg"))); BufferedReader constructors = new BufferedReader(new StringReader(files.get("config/constructors.txt")))) {
                Map<String, ClassMapping> classes = new HashMap<>();

                ClassMapping current = null;
                for (String line = contents.readLine(); line != null; line = contents.readLine()) {
                    if (line.charAt(0) != '\t') {
                        String[] parts = line.split(" ");
                        if (parts.length != 2)
                            throw new IllegalStateException("Unexpected line split: " + Arrays.toString(parts) + " from " + line);

                        classes.put(parts[1], current = new ClassMapping(parts[0], parts[1]));
                    } else {
                        String[] parts = line.substring(1).split(" ");
                        switch (parts.length) {
                            case 2: //Field
                                current.fields.put(parts[1], parts[0]);
                                break;

                            case 3: //Method
                                current.methods.put(parts[2], parts[0] + ' ' + parts[1]);
                                break;

                            default:
                                throw new IllegalStateException("Unexpected line split: " + Arrays.toString(parts) + " from " + line);
                        }
                    }
                }

                Pattern classFinder = Pattern.compile("L([^;]+);");
                for (String line = constructors.readLine(); line != null; line = constructors.readLine()) {
                    if (line.startsWith("#")) continue;
                    String[] parts = line.split(" ");

                    if (parts.length != 3)
                        throw new IllegalStateException("Unexpected line length: " + Arrays.toString(parts) + " from " + line);

                    current = classes.get(parts[1]);
                    if (current == null) {
                        //Anonymous classes will often not have mappings, ATing them would be pointless as they're inaccessible anyway
                        //System.err.println("Unable to find " + parts[1] + " for constructor");
                        continue;
                    }

                    String desc = parts[2];
                    StringBuffer buf = new StringBuffer("<init> ");

                    Matcher matcher = classFinder.matcher(desc);
                    while (matcher.find()) {
                        ClassMapping type = classes.get(matcher.group(1));
                        matcher.appendReplacement(buf, Matcher.quoteReplacement('L' + (type == null ? matcher.group(1) : type.notchName) + ';'));
                    }
                    matcher.appendTail(buf);

                    current.constructors.add(buf.toString());
                }

                return classes;
            } catch (IOException e) {
                throw new RuntimeException("Error processing SRG mappings", e);
            }
        });

        Future<Map<String, String>> mcpTask = executor.submit(() -> {
            HashSet set = new HashSet();
            set.add("methods.csv");
            set.add("fields.csv");
            Map<String, String> files = download(String.format("http://export.mcpbot.bspk.rs/mcp_snapshot_nodoc/%1$s/mcp_snapshot_nodoc-%1$s.zip", "20181106-1.13.1"), set);
            try (BufferedReader methodList = new BufferedReader(new StringReader(files.get("methods.csv"))); BufferedReader fieldList = new BufferedReader(new StringReader(files.get("fields.csv")))) {
                Map<String, String> mappings = new HashMap<>();

                readMcpMappings(methodList, mappings);
                readMcpMappings(fieldList, mappings);

                return mappings;
            } catch (IOException e) {
                throw new RuntimeException("Error unjoining SRG names", e);
            }
        });

        Map<String, ClassMapping> srg = srgTask.get();
        Map<String, String> mcp = mcpTask.get();
        executor.shutdown();

        List<String> transforms = new ArrayList<>();
        Set<String> seenTransforms = new HashSet<>();
        Scanner scanner = new Scanner(System.in);
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.contains("##")) {
                transforms.add(line);
                continue;
            }

            String name = line.substring(line.lastIndexOf(' ') + 1);
            int split = name.lastIndexOf('/');
            if (split < 0) {
                System.err.println("Bad line: " + line);
                System.out.println("\tCorrect AT format: <visibility> <type> <targetClass>/<target>");
                transforms.add("#??? " + line);
                continue;
            }
            String targetClass = name.substring(0, split++).replace('.', '/');
            String target = name.substring(split);

            ClassMapping mappings = srg.get(targetClass);
            if (mappings == null) {
                transforms.add("#??? " + line);
                System.err.println("Bad line: " + line);
                System.out.println("\tNo class found: " + targetClass);
                continue;
            }

            // Determine parameters
            split = line.indexOf(' ');
            String type = line.substring(split+1).replaceAll(" .*", "").trim();
            String visibility = line.substring(0,split).trim();

            if (!visibilityPattern.matcher(visibility).matches()) {
                System.out.println("Bad line: " + line);
                System.out.println("\tvisibility must be one of public, protected, default or private, possibly suffixed by -f");
                continue;
            }

            String[] result;
            if ("method".equals(type)) {
                if ("<init>".equals(target)) {
                    //We're looking for constructors (which are inherently ambiguous without parameters)
                    switch (mappings.constructors.size()) {
                        case 0:
                            System.err.println("No constructors found for " + targetClass);
                            continue;

                        case 1:
                            String match = mappings.constructors.iterator().next();
                            result = new String[] {match};
                            break;

                        default:
                            result = mappings.constructors.toArray(new String[0]);
                    }
                } else {
                    //Find all the possible SRG -> MCP mappings for the target class
                    Set<Entry<String, String>> options = mcp.entrySet().stream().filter(entry -> mappings.methods.containsKey(entry.getKey())).collect(Collectors.toSet());

                    Set<String> matches;
                    if (!target.startsWith("func_")) {
                        //Find all the matching MCP names for the target
                        matches = options.stream().filter(entry -> entry.getValue().equals(target)).map(Entry::getKey).collect(Collectors.toSet());
                    } else {
                        //Looks like there isn't an MCP name for the target, search by SRG instead
                        matches = options.stream().map(Entry::getKey).filter(entry -> entry.equals(target)).collect(Collectors.toSet());
                    }

                    switch (matches.size()) {
                        case 0:
                            System.err.println("Unable to find any matching method names for " + target + " (did find " + options + ')');
                            continue;

                        case 1: {
                            System.out.println("Found mapping for " + target);
                            String match = matches.iterator().next();
                            result = new String[] {mappings.methods.get(match)};
                            break;
                        }

                        default:
                            System.out.println("Fuzzing " + target + ", found " + matches.size() + " matches");
                            result = matches.stream().map(mappings.methods::get).toArray(String[]::new);
                    }
                }
            } else if ("field".equals(type)) {
                //Find all the possible SRG -> MCP mappings for the target class
                Set<Entry<String, String>> options = mcp.entrySet().stream().filter(entry -> mappings.fields.containsKey(entry.getKey())).collect(Collectors.toSet());

                Set<String> matches;
                if (!target.startsWith("field_")) {
                    //Find all the matching MCP names for the target
                    matches = options.stream().filter(entry -> entry.getValue().equals(target)).map(Entry::getKey).collect(Collectors.toSet());
                } else {
                    //Looks like there isn't an MCP name for the target, search by SRG instead
                    matches = options.stream().map(Entry::getKey).filter(entry -> entry.equals(target)).collect(Collectors.toSet());
                }

                switch (matches.size()) {
                    case 0:
                        System.err.println("Unable to find any matching field names for " + target + " (did find " + options + ')');
                        continue;

                    case 1: {
                        String match = matches.iterator().next();
                        result = new String[] {mappings.fields.get(match)};
                        break;
                    }

                    default:
                        result = matches.stream().map(mappings.fields::get).toArray(String[]::new);
                }

                System.out.println("Signature for " + target + "?");
                String signature = scanner.nextLine();
                for (int i = 0; i < result.length; i++) {
                    result[i] += " " + signature;
                }
            } else {
                System.out.println("Bad line: " + line);
                System.out.println("\ttype must be method or field");
                transforms.add("#??? " + line);
                continue;
            }

            for (String transform : result) {
                transform = visibility + " " + type + " " + mappings.notchName + ' ' + transform;

                //Fuzzed mappings can result in already transforming some methods
                if (!seenTransforms.contains(transform)) {
                    seenTransforms.add(transform);
                    transforms.add(transform + " ## " + targetClass + "#" + target);
                }
            }
        }

        transforms.forEach(System.out::println);
        Files.write(atPath, transforms, Charset.defaultCharset());
    }

    static void readMcpMappings(BufferedReader contents, Map<String, String> mappings) throws IOException {
        contents.readLine(); //Skip the header line

        for (String line = contents.readLine(); line != null; line = contents.readLine()) {
            int first = line.indexOf(',');

            String srg = line.substring(0, first++);
            String mcp = line.substring(first, line.indexOf(',', first));

            mappings.put(srg, mcp);
        }
    }

    private static Map<String, String> download(String url, Set<String> files) {
        HttpURLConnection connection = null;
        try {
            URL target = new URL(url);
            connection = (HttpURLConnection) target.openConnection();

            try (InputStream in = connection.getInputStream(); ZipInputStream zip = new ZipInputStream(in)) {
                Map<String, String> out = new HashMap<>();

                for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    //System.out.println(entry.getName());

                    if (!entry.isDirectory()) {
                        if (files.remove(entry.getName())) {
                            byte[] content = new byte[4096];
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

                            int read;
                            while ((read = zip.read(content)) != -1) {
                                bytes.write(content, 0, read);
                            }

                            out.put(entry.getName(), new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                            if (files.isEmpty()) return out;
                        }
                    }

                    zip.closeEntry();
                }
            }

            throw new RuntimeException("Unable to find targets in " + url + " (missed " + files + ')');
        } catch (ZipException e) {
            throw new SecurityException("Invalid (non?) zip response from " + url, e);
        } catch (IOException e) {
            throw new RuntimeException("Error downloading mappings from " + url, e);
        } finally {
            connection.disconnect();
        }
    }
}