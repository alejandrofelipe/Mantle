import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.reflect.AccessFlag;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Compiler-driven member renames (R4 rename tooling, M7 Phase 0.5b). Reads javac errors (a Gradle compile log, flow gate
 * recommended) plus the member table of FindApiRenames.java and edits ONLY the reported spots:
 * <ol>
 *   <li>(a) {@code cannot find symbol: method|variable X} (also inside {@code invalid method reference}) at the reported
 *       line/column, when the location type or one of its supertypes (source + jar) has a table rename {@code X -> Y}
 *       with a compatible arity;</li>
 *   <li>(b) {@code method does not override or implement a method from a supertype}: renames the declaration when a
 *       supertype method with that name and arity was renamed;</li>
 *   <li>(c) reports (never edits) "possible dead overrides": methods without {@code @Override} whose name/arity matches a
 *       renamed or hinted supertype method that no longer exists - they compile and are never called.</li>
 * </ol>
 * <pre>
 * java CompilerRenames.java --log LOG --members TSV --jar NEW.jar --root DIR[,DIR...] [--apply]
 * </pre>
 * Dry run unless {@code --apply}. Only files under a {@code --root} are edited; all {@code src} trees under the roots are
 * indexed for supertypes (pass Mantle's root too when fixing Tinkers). Exit 0 ok, 1 bad arguments.
 */
public class CompilerRenames {
  // ---------- inputs ----------
  record JErr(Path file, int line, int col, String msg, String symbol, String location, String srcLine) {}

  record Row(String kind, String oldOwner, String newOwner, String oldName, String newName, String oldDesc, String newDesc, boolean isStatic, String how) {}

  record Hint(String owner, String newOwner, String name, String message) {}

  record JarCls(String name, String sup, List<String> ifs, Map<String, Set<Integer>> methods) {}

  // ---------- source model ----------
  static final class SrcFile {
    Path path; String text, masked; int[] lineStarts; String pkg = "";
    List<String> imports = new ArrayList<>(), wildcards = new ArrayList<>();
    List<TypeDecl> types = new ArrayList<>();
    List<MethodDecl> methods = new ArrayList<>();
  }

  static final class TypeDecl {
    String simple; String fqn; List<String> supers = new ArrayList<>(); TypeDecl outer; SrcFile file; boolean anonymous; int start, end;
  }

  static final class MethodDecl {
    String name; int nameStart, headerStart, arity; boolean override, isStatic; TypeDecl owner; List<String> paramTypes;
  }

  record Edit(Path file, int start, String oldText, String newText, String why) {}

  static final Pattern ERR = Pattern.compile("^(.+?\\.java):(\\d+): error: (.*)$");
  static final Set<String> KEYWORDS = Set.of("if", "for", "while", "switch", "catch", "synchronized", "return", "new", "throw", "else",
    "case", "do", "try", "finally", "assert", "yield");

  public static void main(String[] args) throws Exception {
    Map<String, String> opt = new HashMap<>();
    boolean apply = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--apply")) { apply = true; continue; }
      if (!args[i].startsWith("--") || i + 1 >= args.length) usage("bad argument: " + args[i]);
      opt.put(args[i].substring(2), args[++i]);
    }
    for (String k : List.of("log", "members", "jar", "root")) if (!opt.containsKey(k)) usage("--" + k + " is required");
    List<Path> roots = new ArrayList<>();
    for (String r : opt.get("root").split(",")) roots.add(Path.of(r.trim()).toAbsolutePath().normalize());
    for (Path p : List.of(Path.of(opt.get("log")), Path.of(opt.get("members")), Path.of(opt.get("jar"))))
      if (!Files.isRegularFile(p)) usage("not found: " + p);

    List<JErr> errs = parseLog(Path.of(opt.get("log")));
    List<Row> rows = new ArrayList<>();
    List<Hint> hints = new ArrayList<>();
    readMembers(Path.of(opt.get("members")), rows, hints);
    Map<String, JarCls> jar = readJar(Path.of(opt.get("jar")));
    CompilerRenames tool = new CompilerRenames(roots, rows, hints, jar);
    tool.run(errs, apply, opt.get("log"));
  }

  static void usage(String error) {
    System.out.println("ERROR: " + error);
    System.out.println("usage: java CompilerRenames.java --log LOG --members TSV --jar NEW.jar --root DIR[,DIR...] [--apply]");
    System.exit(1);
  }

  // ---------- parsing inputs ----------
  static List<JErr> parseLog(Path log) throws IOException {
    List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
    LinkedHashMap<String, JErr> out = new LinkedHashMap<>();
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = ERR.matcher(lines.get(i));
      if (!m.matches()) continue;
      // Gradle repeats every error in its failure summary, indented: strip that indent from the source/caret lines too
      int indent = 0;
      while (indent < lines.get(i).length() && lines.get(i).charAt(indent) == ' ') indent++;
      final int ind = indent;
      java.util.function.IntFunction<String> at = k -> {
        String s = k < lines.size() ? lines.get(k) : "";
        return s.length() >= ind && s.substring(0, ind).isBlank() ? s.substring(ind) : s;
      };
      String src = at.apply(i + 1);
      int col = -1;
      if (at.apply(i + 2).matches("^[ \\t]*\\^\\s*$")) col = at.apply(i + 2).indexOf('^');
      String sym = "", loc = "";
      for (int j = i + 1; j < Math.min(i + 9, lines.size()); j++) {
        if (ERR.matcher(lines.get(j)).matches()) break;
        Matcher s = Pattern.compile("^\\s*symbol:\\s*(.*)$").matcher(lines.get(j));
        if (s.matches()) sym = s.group(1).trim();
        Matcher l = Pattern.compile("^\\s*location:\\s*(.*)$").matcher(lines.get(j));
        if (l.matches()) loc = l.group(1).trim();
      }
      Path f = Path.of(m.group(1).trim()).toAbsolutePath().normalize();
      JErr e = new JErr(f, Integer.parseInt(m.group(2)), col, m.group(3).trim(), sym, loc, src);
      out.putIfAbsent(f + "|" + e.line + "|" + col + "|" + e.msg + "|" + sym + "|" + loc, e);
    }
    rawErrors = (int) lines.stream().filter(l -> ERR.matcher(l).matches()).count();
    return new ArrayList<>(out.values());
  }

  static int rawErrors;

  static void readMembers(Path tsv, List<Row> rows, List<Hint> hints) throws IOException {
    for (String l : Files.readAllLines(tsv, StandardCharsets.UTF_8)) {
      if (l.isBlank() || l.startsWith("#")) continue;
      String[] f = l.split("\t", 9);
      if (f.length < 9) continue;
      if (f[0].equals("hint")) hints.add(new Hint(f[1], f[2], f[3], f[8]));
      else rows.add(new Row(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7].equals("1"), f[8]));
    }
  }

  static Map<String, JarCls> readJar(Path jarPath) throws IOException {
    Map<String, JarCls> out = new HashMap<>();
    try (ZipFile z = new ZipFile(jarPath.toFile())) {
      for (var e : Collections.list(z.entries())) {
        if (!e.getName().endsWith(".class")) continue;
        ClassModel cm = ClassFile.of().parse(z.getInputStream(e).readAllBytes());
        Map<String, Set<Integer>> ms = new HashMap<>();
        for (MethodModel mm : cm.methods()) {
          if (mm.flags().has(AccessFlag.PRIVATE) || mm.flags().has(AccessFlag.SYNTHETIC)) continue;
          ms.computeIfAbsent(mm.methodName().stringValue(), k -> new HashSet<>()).add(descParams(mm.methodType().stringValue()).size());
        }
        String n = cm.thisClass().asInternalName();
        out.put(n, new JarCls(n, cm.superclass().map(ClassEntry::asInternalName).orElse(null),
          cm.interfaces().stream().map(ClassEntry::asInternalName).toList(), ms));
      }
    }
    return out;
  }

  /** Simple display names of a method descriptor's parameters (int, String, Foo for a/b/Outer$Foo, int[]). */
  static List<String> descParams(String desc) {
    List<String> out = new ArrayList<>();
    int i = desc.indexOf('(') + 1, end = desc.indexOf(')');
    while (i < end) {
      int dims = 0;
      while (desc.charAt(i) == '[') { dims++; i++; }
      String t;
      char c = desc.charAt(i);
      if (c == 'L') { int j = desc.indexOf(';', i); String n = desc.substring(i + 1, j); t = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('$')) + 1); i = j + 1; }
      else { t = switch (c) { case 'B' -> "byte"; case 'C' -> "char"; case 'D' -> "double"; case 'F' -> "float"; case 'I' -> "int"; case 'J' -> "long"; case 'S' -> "short"; case 'Z' -> "boolean"; default -> "?"; }; i++; }
      out.add(t + "[]".repeat(dims));
    }
    return out;
  }

  // ---------- state ----------
  final List<Path> roots;
  final Map<String, List<Row>> rowsByOwner = new HashMap<>();
  final Map<String, List<Hint>> hintsByOwner = new HashMap<>();
  final Map<String, JarCls> jar;
  final Map<String, List<String>> jarBySimple = new HashMap<>();
  final Map<Path, SrcFile> files = new LinkedHashMap<>();
  final Map<String, TypeDecl> srcByFqn = new HashMap<>();
  final Map<String, List<TypeDecl>> srcBySimple = new HashMap<>();

  CompilerRenames(List<Path> roots, List<Row> rows, List<Hint> hints, Map<String, JarCls> jar) throws IOException {
    this.roots = roots;
    this.jar = jar;
    for (Row r : rows) {
      rowsByOwner.computeIfAbsent(r.newOwner, k -> new ArrayList<>()).add(r);
      if (!r.oldOwner.equals(r.newOwner)) rowsByOwner.computeIfAbsent(r.oldOwner, k -> new ArrayList<>()).add(r);
    }
    for (Hint h : hints) {
      hintsByOwner.computeIfAbsent(h.newOwner, k -> new ArrayList<>()).add(h);
      if (!h.owner.equals(h.newOwner)) hintsByOwner.computeIfAbsent(h.owner, k -> new ArrayList<>()).add(h);
    }
    for (String n : jar.keySet()) {
      String s = n.substring(n.lastIndexOf('/') + 1);
      jarBySimple.computeIfAbsent(s.substring(s.lastIndexOf('$') + 1), k -> new ArrayList<>()).add(n);
      if (s.contains("$")) jarBySimple.computeIfAbsent(s.replace('$', '.'), k -> new ArrayList<>()).add(n);
    }
    for (Path root : roots) {
      Path src = root.resolve("src");
      if (!Files.isDirectory(src)) src = root;
      try (Stream<Path> s = Files.walk(src)) {
        for (Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) index(p.toAbsolutePath().normalize());
      }
    }
  }

  // ---------- source indexing ----------
  void index(Path p) throws IOException {
    SrcFile f = new SrcFile();
    f.path = p;
    f.text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    f.masked = mask(f.text);
    List<Integer> ls = new ArrayList<>(List.of(0));
    for (int i = 0; i < f.text.length(); i++) if (f.text.charAt(i) == '\n') ls.add(i + 1);
    f.lineStarts = ls.stream().mapToInt(Integer::intValue).toArray();
    Matcher pm = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(f.masked);
    if (pm.find()) f.pkg = pm.group(1);
    Matcher im = Pattern.compile("(?m)^\\s*import\\s+(static\\s+)?([\\w.]+)(\\.\\*)?\\s*;").matcher(f.masked);
    while (im.find()) {
      if (im.group(1) != null) continue;
      if (im.group(3) != null) f.wildcards.add(im.group(2)); else f.imports.add(im.group(2));
    }
    scan(f);
    files.put(p, f);
    for (TypeDecl t : f.types) {
      if (t.anonymous) continue;
      srcByFqn.put(t.fqn, t);
      srcBySimple.computeIfAbsent(t.simple, k -> new ArrayList<>()).add(t);
    }
  }

  /** Blanks comments and the contents of string/char/text-block literals, keeping every offset and newline. */
  static String mask(String s) {
    char[] o = s.toCharArray();
    int n = s.length(), i = 0;
    while (i < n) {
      char c = s.charAt(i);
      if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') { while (i < n && s.charAt(i) != '\n') { if (o[i] != '\r') o[i] = ' '; i++; } continue; }
      if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
        int j = s.indexOf("*/", i + 2); j = j < 0 ? n : j + 2;
        for (int k = i; k < j; k++) if (o[k] != '\n' && o[k] != '\r') o[k] = ' ';
        i = j; continue;
      }
      if (c == '"' && s.startsWith("\"\"\"", i)) {
        int j = i + 3;
        while (j < n && !s.startsWith("\"\"\"", j)) { if (s.charAt(j) == '\\') j++; j++; }
        for (int k = i + 3; k < Math.min(j, n); k++) if (o[k] != '\n' && o[k] != '\r') o[k] = 'x';
        i = Math.min(j + 3, n); continue;
      }
      if (c == '"' || c == '\'') {
        int j = i + 1;
        while (j < n && s.charAt(j) != c && s.charAt(j) != '\n') { if (s.charAt(j) == '\\') j++; j++; }
        for (int k = i + 1; k < Math.min(j, n); k++) o[k] = 'x';
        i = j + 1; continue;
      }
      i++;
    }
    return new String(o);
  }

  enum Kind { TYPE, METHOD, BLOCK, INLINE }

  static final class Frame {
    Kind kind; int lastDelim, parenAtOpen; TypeDecl type;
    Frame(Kind k, int d, int p, TypeDecl t) { kind = k; lastDelim = d; parenAtOpen = p; type = t; }
  }

  static final Pattern TYPE_DECL = Pattern.compile("(?<![\\w.$])(class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");

  void scan(SrcFile f) {
    String m = f.masked;
    Deque<Frame> stack = new ArrayDeque<>();
    stack.push(new Frame(Kind.BLOCK, -1, 0, null));
    int paren = 0;
    for (int i = 0; i < m.length(); i++) {
      char c = m.charAt(i);
      Frame top = stack.peek();
      switch (c) {
        case '(' -> paren++;
        case ')' -> paren--;
        case ';' -> { if (paren == top.parenAtOpen) top.lastDelim = i; }
        case '{' -> {
          String header = m.substring(top.lastDelim + 1, i);
          TypeDecl enclosing = enclosingType(stack);
          String anonSuper = anonymousSuper(header);
          if (anonSuper != null) {
            TypeDecl t = new TypeDecl();
            t.anonymous = true; t.simple = "<anonymous " + anonSuper + ">"; t.supers.add(anonSuper); t.outer = enclosing; t.file = f; t.start = i;
            t.fqn = (enclosing != null ? enclosing.fqn : f.pkg) + ".<anonymous>";
            f.types.add(t);
            stack.push(new Frame(Kind.TYPE, i, paren, t));
          } else if (paren > top.parenAtOpen) {
            stack.push(new Frame(Kind.INLINE, i, paren, null));
          } else {
            Matcher tm = TYPE_DECL.matcher(header);
            TypeDecl decl = null;
            while (tm.find()) {
              String after = header.substring(tm.end()).trim();
              if (after.isEmpty() || after.startsWith("<") || after.startsWith("(") || after.startsWith("extends") || after.startsWith("implements") || after.startsWith("permits")) {
                decl = new TypeDecl();
                decl.simple = tm.group(2); decl.outer = enclosing; decl.file = f; decl.start = i;
                decl.fqn = (enclosing != null ? enclosing.fqn : (f.pkg.isEmpty() ? "" : f.pkg + ".")) + (enclosing != null ? "." : "") + decl.simple;
                String rest = stripGenerics(header.substring(tm.end())).replaceAll("\\([^)]*\\)", " ");
                Matcher sm = Pattern.compile("\\b(extends|implements)\\s+([\\w.$\\s,]+?)(?=\\b(?:implements|permits|extends)\\b|$)").matcher(rest);
                while (sm.find()) for (String s : sm.group(2).split(",")) if (!s.isBlank()) decl.supers.add(s.trim());
                break;
              }
            }
            if (decl != null) {
              f.types.add(decl);
              stack.push(new Frame(Kind.TYPE, i, paren, decl));
            } else if (top.kind == Kind.TYPE) {
              MethodDecl md = methodHeader(header, top.lastDelim + 1, top.type);
              if (md != null) f.methods.add(md);
              stack.push(new Frame(md != null ? Kind.METHOD : Kind.BLOCK, i, paren, null));
            } else {
              stack.push(new Frame(Kind.BLOCK, i, paren, null));
            }
          }
        }
        case '}' -> {
          if (stack.size() > 1) {
            Frame done = stack.pop();
            if (done.type != null) done.type.end = i;
            if (done.kind != Kind.INLINE) stack.peek().lastDelim = i;
          }
        }
        default -> { }
      }
    }
  }

  static TypeDecl enclosingType(Deque<Frame> stack) {
    for (Frame fr : stack) if (fr.kind == Kind.TYPE) return fr.type;
    return null;
  }

  /** {@code ... new Foo<..>(args) } at the end of a header -> "Foo", else null. */
  static String anonymousSuper(String header) {
    Matcher nm = Pattern.compile("(?<![\\w$])new\\s+([A-Za-z_$][\\w$.]*)\\s*").matcher(header);
    String found = null;
    while (nm.find()) {
      int i = nm.end();
      if (i < header.length() && header.charAt(i) == '<') { i = skipBalanced(header, i, '<', '>'); if (i < 0) continue; while (i < header.length() && Character.isWhitespace(header.charAt(i))) i++; }
      if (i >= header.length() || header.charAt(i) != '(') continue;
      int j = skipBalanced(header, i, '(', ')');
      if (j >= 0 && header.substring(j).isBlank()) found = nm.group(1);
    }
    return found;
  }

  /** Index just past the bracket matching the one at {@code i}, or -1. */
  static int skipBalanced(String s, int i, char open, char close) {
    int d = 0;
    for (int k = i; k < s.length(); k++) {
      char c = s.charAt(k);
      if (c == open) d++;
      else if (c == close && --d == 0) return k + 1;
    }
    return -1;
  }

  static String stripGenerics(String s) {
    StringBuilder b = new StringBuilder();
    int d = 0;
    for (char c : s.toCharArray()) {
      if (c == '<') d++;
      else if (c == '>') d = Math.max(0, d - 1);
      else if (d == 0) b.append(c);
    }
    return b.toString();
  }

  /** A method declaration header (text between the previous member and its '{'), or null. */
  static MethodDecl methodHeader(String header, int offset, TypeDecl owner) {
    String h = header.replaceAll("\\s+$", "");
    Matcher th = Pattern.compile("\\)\\s*throws\\s+[\\w.$\\s,<>]+$").matcher(h);
    if (th.find()) h = h.substring(0, th.start() + 1);
    if (!h.endsWith(")") || h.contains("->") || h.contains("=")) return null;
    // find the '(' matching the final ')'
    int d = 0, open = -1;
    for (int k = h.length() - 1; k >= 0; k--) {
      char c = h.charAt(k);
      if (c == ')') d++;
      else if (c == '(' && --d == 0) { open = k; break; }
    }
    if (open <= 0) return null;
    int e = open;
    while (e > 0 && Character.isWhitespace(h.charAt(e - 1))) e--;
    int s = e;
    while (s > 0 && (Character.isJavaIdentifierPart(h.charAt(s - 1)))) s--;
    if (s == e) return null;
    String name = h.substring(s, e);
    if (KEYWORDS.contains(name) || !Character.isJavaIdentifierStart(name.charAt(0))) return null;
    if (owner != null && name.equals(owner.simple)) return null; // constructor
    String before = h.substring(0, s).replaceAll("\\s+$", "");
    if (before.isEmpty()) return null;
    char last = before.charAt(before.length() - 1);
    if (!(Character.isJavaIdentifierPart(last) || last == '>' || last == ']')) return null;
    Matcher lastWord = Pattern.compile("([A-Za-z_$][\\w$]*)$").matcher(before);
    if (lastWord.find() && KEYWORDS.contains(lastWord.group(1))) return null;
    MethodDecl md = new MethodDecl();
    md.name = name; md.nameStart = offset + s; md.headerStart = offset; md.owner = owner;
    md.override = Pattern.compile("@(java\\.lang\\.)?Override\\b").matcher(before).find();
    md.isStatic = Pattern.compile("(?<![\\w$])static(?![\\w$])").matcher(stripAnnotations(before)).find();
    md.paramTypes = new ArrayList<>();
    for (String p : splitTop(h.substring(open + 1, h.length() - 1))) {
      String t = stripAnnotations(p).replaceAll("(?<![\\w$])final(?![\\w$])", "").trim();
      t = stripGenerics(t).replace("...", "[]").trim();
      int sp = t.lastIndexOf(' ');
      if (sp > 0) t = t.substring(0, sp).trim();
      t = t.replaceAll("\\s+", "");
      md.paramTypes.add(t.substring(t.lastIndexOf('.') + 1));
    }
    md.arity = md.paramTypes.size();
    return md;
  }

  static String stripAnnotations(String s) {
    StringBuilder b = new StringBuilder();
    int i = 0;
    while (i < s.length()) {
      if (s.charAt(i) == '@' && i + 1 < s.length() && Character.isJavaIdentifierStart(s.charAt(i + 1))) {
        i++;
        while (i < s.length() && (Character.isJavaIdentifierPart(s.charAt(i)) || s.charAt(i) == '.')) i++;
        int j = i;
        while (j < s.length() && Character.isWhitespace(s.charAt(j))) j++;
        if (j < s.length() && s.charAt(j) == '(') { int k = skipBalanced(s, j, '(', ')'); i = k < 0 ? s.length() : k; }
        continue;
      }
      b.append(s.charAt(i++));
    }
    return b.toString();
  }

  /** Splits on top-level commas (outside &lt;&gt;, (), []); blank input -> empty list. */
  static List<String> splitTop(String s) {
    List<String> out = new ArrayList<>();
    if (s.isBlank()) return out;
    int d = 0, start = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '<' || c == '(' || c == '[') d++;
      else if (c == '>' || c == ')' || c == ']') d--;
      else if (c == ',' && d == 0) { out.add(s.substring(start, i)); start = i + 1; }
    }
    out.add(s.substring(start));
    return out;
  }

  // ---------- type resolution ----------
  /** Resolved type: a jar class (internal name) or a source type. */
  record Ref(String jarName, TypeDecl src) {}

  List<Ref> resolve(String name, SrcFile ctx) {
    String n = stripGenerics(name).replace("[]", "").replace("...", "").trim();
    Matcher anon = Pattern.compile("^<anonymous\\s+(.+)>$").matcher(n);
    if (anon.matches()) n = stripGenerics(anon.group(1)).trim();
    if (n.isEmpty()) return List.of();
    String[] seg = n.split("\\.");
    // fully qualified (first segment lower case)
    if (seg.length > 1 && Character.isLowerCase(seg[0].charAt(0))) {
      List<Ref> fq = byFqn(n);
      if (!fq.isEmpty()) return fq;
    }
    List<Ref> heads = resolveSimple(seg[0], ctx);
    if (seg.length == 1) return heads;
    List<Ref> out = new ArrayList<>();
    for (Ref h : heads) {
      String tail = String.join(".", Arrays.copyOfRange(seg, 1, seg.length));
      if (h.jarName != null && jar.containsKey(h.jarName + "$" + tail.replace('.', '$'))) out.add(new Ref(h.jarName + "$" + tail.replace('.', '$'), null));
      if (h.src != null && srcByFqn.containsKey(h.src.fqn + "." + tail)) out.add(new Ref(null, srcByFqn.get(h.src.fqn + "." + tail)));
    }
    return out;
  }

  List<Ref> byFqn(String dotted) {
    List<Ref> out = new ArrayList<>();
    if (srcByFqn.containsKey(dotted)) out.add(new Ref(null, srcByFqn.get(dotted)));
    String[] seg = dotted.split("\\.");
    for (int cut = seg.length - 1; cut >= 1; cut--) {
      String internal = String.join("/", Arrays.copyOfRange(seg, 0, cut)) + "/" + String.join("$", Arrays.copyOfRange(seg, cut, seg.length));
      if (jar.containsKey(internal)) { out.add(new Ref(internal, null)); break; }
    }
    return out;
  }

  List<Ref> resolveSimple(String s, SrcFile ctx) {
    List<Ref> out = new ArrayList<>();
    if (ctx != null) {
      for (TypeDecl t : ctx.types) if (!t.anonymous && t.simple.equals(s)) out.add(new Ref(null, t));
      if (!out.isEmpty()) return out;
      for (String imp : ctx.imports) if (imp.endsWith("." + s)) { out.addAll(byFqn(imp)); if (!out.isEmpty()) return out; }
      out.addAll(byFqn((ctx.pkg.isEmpty() ? "" : ctx.pkg + ".") + s));
      if (!out.isEmpty()) return out;
      for (String w : ctx.wildcards) out.addAll(byFqn(w + "." + s));
      if (!out.isEmpty()) return out;
    }
    List<String> js = jarBySimple.getOrDefault(s, List.of());
    if (js.size() == 1) out.add(new Ref(js.get(0), null));
    List<TypeDecl> ss = srcBySimple.getOrDefault(s, List.of());
    if (ss.size() == 1) out.add(new Ref(null, ss.get(0)));
    return out;
  }

  /** Jar ancestors (including itself) of resolved types; {@code srcMethodsOut} collects source ancestors' methods. */
  Set<String> jarAncestors(List<Ref> refs, List<MethodDecl> srcMethodsOut, TypeDecl skipOwnMethods) {
    Set<String> out = new LinkedHashSet<>();
    Deque<Ref> todo = new ArrayDeque<>(refs);
    Set<Object> seen = new HashSet<>();
    while (!todo.isEmpty()) {
      Ref r = todo.pop();
      if (r.jarName != null) {
        if (!seen.add(r.jarName)) continue;
        out.add(r.jarName);
        JarCls c = jar.get(r.jarName);
        if (c == null) continue;
        if (c.sup != null) todo.push(new Ref(c.sup, null));
        for (String i : c.ifs) todo.push(new Ref(i, null));
      } else if (r.src != null) {
        if (!seen.add(r.src)) continue;
        if (srcMethodsOut != null && r.src != skipOwnMethods)
          for (MethodDecl m : r.src.file.methods) if (m.owner == r.src) srcMethodsOut.add(m);
        for (String s : r.src.supers) todo.addAll(resolve(s, r.src.file));
      }
    }
    return out;
  }

  // ---------- matching ----------
  static boolean arityOk(String desc, int n) {
    List<String> p = descParams(desc);
    return p.size() == n || (!p.isEmpty() && p.get(p.size() - 1).endsWith("[]") && n >= p.size() - 1);
  }

  record Match(List<Row> rows, String status) {}

  /** Rows for {@code name} in the given jar types whose arity fits; disambiguates distinct new names by parameter types. */
  Match match(String kind, String name, Set<String> owners, int arity, List<String> argTypes, boolean wantStatic, boolean checkStatic) {
    List<Row> c = new ArrayList<>();
    for (String o : owners)
      for (Row r : rowsByOwner.getOrDefault(o, List.of()))
        if (r.kind.equals(kind) && r.oldName.equals(name) && (!kind.equals("method") || arityOk(r.oldDesc, arity)) && (!checkStatic || r.isStatic == wantStatic) && !c.contains(r))
          c.add(r);
    Set<String> names = new TreeSet<>();
    c.forEach(r -> names.add(r.newName));
    if (names.size() <= 1) return new Match(c, names.isEmpty() ? "none" : "ok");
    if (argTypes != null && kind.equals("method")) {
      int best = -1;
      List<Row> top = new ArrayList<>();
      for (Row r : c) {
        List<String> p = descParams(r.oldDesc);
        int score = 0;
        for (int i = 0; i < Math.min(p.size(), argTypes.size()); i++) if (p.get(i).equals(argTypes.get(i))) score++;
        if (score > best) { best = score; top.clear(); }
        if (score == best) top.add(r);
      }
      Set<String> tn = new TreeSet<>();
      top.forEach(r -> tn.add(r.newName));
      if (tn.size() == 1) return new Match(top, "ok");
    }
    return new Match(c, "ambiguous");
  }

  // ---------- run ----------
  final List<Edit> edits = new ArrayList<>();
  final Set<String> ambiguous = new TreeSet<>(), unresolvedOverrides = new TreeSet<>(), dead = new TreeSet<>(), skipped = new TreeSet<>(), lowSimilarity = new TreeSet<>();

  void run(List<JErr> errs, boolean apply, String logName) throws IOException {
    int symbolErrors = 0, overrideErrors = 0, other = 0;
    for (JErr e : errs) {
      SrcFile f = files.get(e.file);
      boolean underRoot = roots.stream().anyMatch(r -> e.file.startsWith(r));
      boolean sym = e.msg.equals("cannot find symbol") || e.msg.startsWith("invalid method reference");
      boolean ovr = e.msg.startsWith("method does not override or implement a method from a supertype");
      if (!sym && !ovr) { other++; continue; }
      if (f == null || !underRoot) { skipped.add(rel(e.file) + ":" + e.line + " not under --root: " + e.msg); continue; }
      if (sym && (e.symbol.startsWith("method ") || e.symbol.startsWith("variable "))) { symbolErrors++; onSymbol(e, f); }
      else if (ovr) { overrideErrors++; onOverride(e, f); }
      else other++;
    }
    findDeadOverrides();

    // apply / report
    Map<Path, List<Edit>> byFile = new TreeMap<>();
    Set<String> seen = new HashSet<>();
    for (Edit ed : edits) if (seen.add(ed.file + "|" + ed.start)) byFile.computeIfAbsent(ed.file, k -> new ArrayList<>()).add(ed);
    int count = 0, written = 0;
    System.out.println("compiler-renames: " + (apply ? "APPLY" : "DRY RUN (nothing written; pass --apply to rewrite)"));
    System.out.println("  log:   " + logName + "  (errors raw=" + rawErrors + " unique=" + errs.size() + "; symbol=" + symbolErrors + " override=" + overrideErrors + " other=" + other + ")");
    System.out.println("  roots: " + roots + "  (" + files.size() + " source files indexed)");
    System.out.println();
    System.out.println("Edits:");
    for (var fe : byFile.entrySet()) {
      SrcFile f = files.get(fe.getKey());
      List<Edit> list = new ArrayList<>(fe.getValue());
      list.sort(Comparator.comparingInt(Edit::start).reversed());
      StringBuilder text = new StringBuilder(f.text);
      for (Edit ed : list) {
        int[] lc = lineCol(f, ed.start);
        System.out.printf("  %s:%d  %s -> %s  [%s]%n", rel(f.path), lc[0], ed.oldText, ed.newText, ed.why);
        text.replace(ed.start, ed.start + ed.oldText.length(), ed.newText);
        count++;
      }
      if (apply) { Files.write(f.path, text.toString().getBytes(StandardCharsets.UTF_8)); written++; }
    }
    section("Low-similarity renames used (matched by descriptor only: review each once)", lowSimilarity);
    section("Ambiguous (several renames fit; not edited)", ambiguous);
    section("Unresolved overrides / hinted calls (no rename for the supertype method; fix by hand)", unresolvedOverrides);
    section("Possible dead overrides (compile, never called: the supertype method was renamed or removed)", dead);
    section("Skipped", skipped);
    if (apply) System.out.println("written=" + written);
    System.out.printf("errors=%d edits=%d files=%d ambiguous=%d unresolvedOverrides=%d deadOverrides=%d%n",
      errs.size(), count, byFile.size(), ambiguous.size(), unresolvedOverrides.size(), dead.size());
  }

  static void section(String title, Set<String> items) {
    if (items.isEmpty()) return;
    System.out.println();
    System.out.println(title + " (" + items.size() + "):");
    items.forEach(s -> System.out.println("  " + s));
  }

  String rel(Path p) {
    for (Path r : roots) if (p.startsWith(r)) return r.relativize(p).toString().replace('\\', '/');
    return p.toString();
  }

  static int[] lineCol(SrcFile f, int pos) {
    int idx = Arrays.binarySearch(f.lineStarts, pos);
    int line = idx >= 0 ? idx : -idx - 2;
    return new int[]{line + 1, pos - f.lineStarts[line]};
  }

  static String lineText(SrcFile f, int line) {
    int s = f.lineStarts[line - 1], e = line < f.lineStarts.length ? f.lineStarts[line] : f.text.length();
    return f.text.substring(s, e).replaceAll("[\\r\\n]+$", "");
  }

  TypeDecl typeAt(SrcFile f, int pos) {
    TypeDecl best = null;
    for (TypeDecl t : f.types) if (t.start <= pos && pos <= t.end && (best == null || t.start > best.start)) best = t;
    return best;
  }

  // (a) cannot find symbol
  void onSymbol(JErr e, SrcFile f) {
    boolean isMethod = e.symbol.startsWith("method ");
    String symText = e.symbol.substring(isMethod ? 7 : 9).trim();
    String name = isMethod ? symText.substring(0, symText.indexOf('(') < 0 ? symText.length() : symText.indexOf('(')).trim() : symText;
    List<String> args = null;
    if (isMethod && symText.contains("(")) {
      args = new ArrayList<>();
      for (String a : splitTop(symText.substring(symText.indexOf('(') + 1, symText.lastIndexOf(')')))) {
        String t = stripGenerics(a).trim();
        args.add(t.substring(t.lastIndexOf('.') + 1));
      }
    }
    String where = rel(f.path) + ":" + e.line;
    // location -> type
    String typeName;
    Matcher vm = Pattern.compile("^variable \\S+ of type (.+)$").matcher(e.location);
    Matcher cm = Pattern.compile("^(?:class|interface|enum|record|@interface) (.+)$").matcher(e.location);
    int linePos = f.lineStarts[e.line - 1];
    List<Ref> refs;
    if (vm.matches() || cm.matches()) {
      typeName = vm.matches() ? vm.group(1) : cm.group(1);
      refs = resolve(typeName, f);
      if (refs.isEmpty() && cm.matches()) { TypeDecl t = typeAt(f, linePos); if (t != null) refs = List.of(new Ref(null, t)); }
    } else if (e.location.isEmpty()) {
      // javac prints no location for 'super.x(..)' / 'this.x(..)': use the enclosing type
      int pos = namePosition(e, f, name);
      if (pos < 0 || !Pattern.compile("(?<![\\w$])(super|this)\\s*\\.\\s*$").matcher(f.masked.substring(linePos, pos)).find()) return;
      TypeDecl t = typeAt(f, pos);
      if (t == null) return;
      typeName = t.simple;
      refs = List.of(new Ref(null, t));
    } else return; // package / unknown location: not a member rename
    if (refs.isEmpty()) return;
    Set<String> owners = jarAncestors(refs, null, null);
    Match m = match(isMethod ? "method" : "field", name, owners, args == null ? -1 : args.size(), args, false, false);
    if (m.status.equals("none")) {
      String hint = hintText(name, owners);
      if (!hint.isEmpty()) unresolvedOverrides.add(where + "  " + name + " (call)" + hint);
      return;
    }
    if (m.status.equals("ambiguous")) { ambiguous.add(where + "  " + name + " -> " + names(m.rows) + "  (" + typeName + ")"); return; }
    Row r = m.rows.get(0);
    int pos = namePosition(e, f, name);
    if (pos < 0) { ambiguous.add(where + "  " + name + " -> " + r.newName + "  (position not found on the line)"); return; }
    edits.add(new Edit(f.path, pos, name, r.newName, "a:" + (e.msg.startsWith("invalid method reference") ? "method-ref" : isMethod ? "call" : "field")
      + " " + simpleOwner(r.newOwner) + " " + r.how));
    noteLowSimilarity(r);
  }

  static String names(List<Row> rows) {
    Set<String> s = new TreeSet<>();
    rows.forEach(r -> s.add(r.newName + "@" + simpleOwner(r.newOwner)));
    return String.join("|", s);
  }

  static String simpleOwner(String internal) { return internal.substring(internal.lastIndexOf('/') + 1).replace('$', '.'); }

  /** Offset of the symbol name at the error's caret (skipping '.', blanks, explicit type arguments, '::'), or -1. */
  int namePosition(JErr e, SrcFile f, String name) {
    if (e.line > f.lineStarts.length) return -1;
    String fileLine = lineText(f, e.line);
    int base = f.lineStarts[e.line - 1];
    String ml = f.masked.substring(base, base + fileLine.length());
    Pattern word = Pattern.compile("(?<![\\w$])" + Pattern.quote(name) + "(?![\\w$])");
    if (e.col >= 0 && fileLine.equals(e.srcLine.replaceAll("[\\r\\n]+$", ""))) {
      int i = e.col;
      while (i < ml.length() && (Character.isWhitespace(ml.charAt(i)) || ml.charAt(i) == '.' || ml.charAt(i) == ':')) i++;
      if (i < ml.length() && ml.charAt(i) == '<') { int k = skipBalanced(ml, i, '<', '>'); if (k > 0) i = k; while (i < ml.length() && Character.isWhitespace(ml.charAt(i))) i++; }
      Matcher w = word.matcher(ml);
      if (w.find(i) && w.start() == i) return base + i;
    }
    Matcher w = word.matcher(ml);
    int found = -1, n = 0;
    while (w.find()) { found = w.start(); n++; }
    return n == 1 ? base + found : -1;
  }

  // (b) method does not override
  void onOverride(JErr e, SrcFile f) {
    int pos = f.lineStarts[e.line - 1] + Math.max(0, e.col);
    MethodDecl md = null;
    for (MethodDecl m : f.methods) if (m.headerStart <= pos && pos < m.nameStart && (md == null || m.headerStart > md.headerStart)) md = m;
    String where = rel(f.path) + ":" + e.line;
    if (md == null || md.owner == null) { unresolvedOverrides.add(where + "  (method declaration not found)"); return; }
    List<MethodDecl> srcSupers = new ArrayList<>();
    Set<String> owners = jarAncestors(List.of(new Ref(null, md.owner)), srcSupers, md.owner);
    String sig = md.owner.simple + "#" + md.name + "/" + md.arity;
    Match m = match("method", md.name, owners, md.arity, md.paramTypes, false, true);
    if (m.status.equals("none")) {
      unresolvedOverrides.add(where + "  " + sig + hintText(md.name, owners));
      return;
    }
    if (m.status.equals("ambiguous")) { ambiguous.add(where + "  " + sig + " -> " + names(m.rows)); return; }
    Row r = m.rows.get(0);
    for (MethodDecl other : f.methods)
      if (other.owner == md.owner && other.name.equals(r.newName) && other.arity == md.arity) {
        unresolvedOverrides.add(where + "  " + sig + " -> " + r.newName + " CONFLICT: " + md.owner.simple + " already declares " + r.newName + "/" + md.arity + " (line " + lineCol(f, other.nameStart)[0] + ")");
        return;
      }
    edits.add(new Edit(f.path, md.nameStart, md.name, r.newName, "b:override " + simpleOwner(r.newOwner) + " " + r.how));
    noteLowSimilarity(r);
  }

  /** Renames matched by descriptor alone whose names share little: listed once each for review (incrementFrameNumber -> endFrame). */
  void noteLowSimilarity(Row r) {
    Matcher sm = Pattern.compile("\\((\\d+\\.\\d+)\\)").matcher(r.how);
    if (sm.find() && Double.parseDouble(sm.group(1)) < 0.5)
      lowSimilarity.add(simpleOwner(r.newOwner) + "#" + r.oldName + " -> " + r.newName + "  " + r.how + "  " + r.oldDesc);
  }

  String hintText(String name, Set<String> owners) {
    for (String o : owners) for (Hint h : hintsByOwner.getOrDefault(o, List.of())) if (h.name.equals(name)) return "  HINT " + simpleOwner(h.newOwner) + "#" + name + ": " + h.message;
    return "";
  }

  // (c) dead overrides
  void findDeadOverrides() {
    for (SrcFile f : files.values()) {
      for (MethodDecl md : f.methods) {
        if (md.override || md.isStatic || md.owner == null) continue;
        List<MethodDecl> srcSupers = new ArrayList<>();
        Set<String> owners = jarAncestors(List.of(new Ref(null, md.owner)), srcSupers, md.owner);
        if (owners.isEmpty()) continue;
        Match m = match("method", md.name, owners, md.arity, md.paramTypes, false, true);
        String hint = hintText(md.name, owners);
        if (m.status.equals("none") && hint.isEmpty()) continue;
        // still overrides something: a source supertype declares it, or a jar supertype still has the name/arity
        if (srcSupers.stream().anyMatch(s -> s.name.equals(md.name) && s.arity == md.arity)) continue;
        boolean stillThere = false;
        for (String o : owners) {
          JarCls c = jar.get(o);
          if (c != null && c.methods.getOrDefault(md.name, Set.of()).contains(md.arity)) { stillThere = true; break; }
        }
        if (stillThere) continue;
        String target = m.status.equals("none") ? "" : " -> super renamed to " + names(m.rows);
        dead.add(rel(f.path) + ":" + lineCol(f, md.nameStart)[0] + "  " + md.owner.simple + "#" + md.name + "/" + md.arity + target + hint);
      }
    }
  }
}
