import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.reflect.AccessFlag;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Jar-to-jar API diff of two Mojang-named Minecraft jars (R4 rename tooling, M7 Phase 0.5a).
 *
 * <pre>
 * java FindApiRenames.java --old OLD.jar --new NEW.jar --out DIR [--overrides FILE] [--prefix net/minecraft/,com/mojang/]
 *                          [--title "MC a -> MC b"]
 * </pre>
 *
 * Writes into DIR:
 * <ul>
 *   <li>{@code classes.tsv}  old -> new class pairs (internal names) and how each was matched</li>
 *   <li>{@code members.tsv}  method/field renames inside class pairs (owner + descriptor), plus manual {@code hint} rows</li>
 *   <li>{@code map.txt}      rename-map rules ({@code class} kind, see Invoke-RenameMap.ps1) + override {@code rule} lines</li>
 *   <li>{@code warnings.txt} "same simple name, different shape" and "same FQN, different shape" warnings</li>
 *   <li>{@code removed.tsv}  classes and members that vanished without a candidate</li>
 * </ul>
 * Class matching, in order: overrides; moved (unique same simple name, unless its shape differs - then it is a suspect);
 * renamed outer classes (inner-class votes); nested classes of renamed outers; member similarity (self-reference aware
 * Jaccard over method/field signatures), same package first, then across packages, repeated while it finds pairs.
 * Member renames: per class pair, gone vs new-only members grouped by remapped descriptor; a 1x1 group is a rename,
 * a larger group is resolved by name similarity (greedy, threshold 0.3).
 */
public class FindApiRenames {
  record Member(String name, String desc, boolean isStatic, boolean isPrivate) {}

  record Cls(String name, String superName, List<String> interfaces, boolean isEnum, List<Member> methods, List<Member> fields) {}

  record Hint(String owner, String name, String message) {}

  record Rule(String kind, String old, String replacement) {}

  /** Overrides {@code moved} line: a member gone from {@code owner} (new jar), reached through another object now. */
  record Moved(String owner, String member, String replacement) {}

  static final String[] DYE_COLORS = {"LIGHT_BLUE", "LIGHT_GRAY", "WHITE", "ORANGE", "MAGENTA", "YELLOW", "LIME", "PINK", "GRAY", "CYAN",
    "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"}; // longest prefixes first (LIGHT_GRAY_ before GRAY_)

  // ---------- reading ----------
  static Map<String, Cls> read(Path jar, List<String> prefixes) throws IOException {
    Map<String, Cls> out = new HashMap<>();
    try (ZipFile z = new ZipFile(jar.toFile())) {
      for (var e : Collections.list(z.entries())) {
        String n = e.getName();
        if (!n.endsWith(".class") || prefixes.stream().noneMatch(n::startsWith)) continue;
        ClassModel cm = ClassFile.of().parse(z.getInputStream(e).readAllBytes());
        List<Member> m = new ArrayList<>(), f = new ArrayList<>();
        for (MethodModel mm : cm.methods()) {
          var fl = mm.flags();
          if (fl.has(AccessFlag.SYNTHETIC) || fl.has(AccessFlag.BRIDGE)) continue;
          String name = mm.methodName().stringValue();
          if (name.startsWith("lambda$") || name.equals("<clinit>")) continue;
          m.add(new Member(name, mm.methodType().stringValue(), fl.has(AccessFlag.STATIC), fl.has(AccessFlag.PRIVATE)));
        }
        for (FieldModel fm : cm.fields()) {
          var fl = fm.flags();
          if (fl.has(AccessFlag.SYNTHETIC)) continue;
          f.add(new Member(fm.fieldName().stringValue(), fm.fieldType().stringValue(), fl.has(AccessFlag.STATIC), fl.has(AccessFlag.PRIVATE)));
        }
        String name = cm.thisClass().asInternalName();
        String sup = cm.superclass().map(ClassEntry::asInternalName).orElse(null);
        List<String> ifs = cm.interfaces().stream().map(ClassEntry::asInternalName).toList();
        out.put(name, new Cls(name, sup, ifs, cm.flags().has(AccessFlag.ENUM), m, f));
      }
    }
    return out;
  }

  // ---------- names ----------
  static String simple(String n) { return n.substring(n.lastIndexOf('/') + 1); }
  static String pkg(String n) { int i = n.lastIndexOf('/'); return i < 0 ? "" : n.substring(0, i); }
  static String outer(String n) { int i = n.indexOf('$'); return i < 0 ? n : n.substring(0, i); }
  static boolean isNested(String n) { return n.indexOf('$') >= 0; }
  static boolean isAnonymousOrLocal(String n) { return Pattern.compile("\\$\\d").matcher(n).find(); }
  static String dotted(String internal) { return internal.replace('/', '.'); }

  static final Pattern REF = Pattern.compile("L([^;]+);");

  /** Remaps class references in a descriptor; {@code self} (and its nested classes) become {@code @}. */
  static String remap(String desc, String self, Map<String, String> classMap) {
    Matcher m = REF.matcher(desc);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String c = m.group(1), r;
      if (self != null && (c.equals(self) || c.startsWith(self + "$"))) r = "@" + c.substring(self.length());
      else r = classMap == null ? c : classMap.getOrDefault(c, c);
      m.appendReplacement(sb, Matcher.quoteReplacement("L" + r + ";"));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  // ---------- similarity ----------
  static final Set<String> OBJECT_METHODS = Set.of("equals(Ljava/lang/Object;)Z", "hashCode()I", "toString()Ljava/lang/String;");

  static Set<String> features(Cls c, Map<String, String> classMap) {
    Set<String> s = new HashSet<>();
    if (c.superName != null && !c.superName.startsWith("java/")) s.add("s" + remap("L" + c.superName + ";", c.name, classMap));
    for (String i : c.interfaces) s.add("i" + remap("L" + i + ";", c.name, classMap));
    for (Member x : c.methods) {
      if (x.isPrivate || OBJECT_METHODS.contains(x.name + x.desc)) continue;
      String d = remap(x.desc, c.name, classMap);
      if (x.name.equals("<init>")) { s.add("c" + d); continue; }
      s.add("m" + x.name + d);
      s.add("d" + d + (x.isStatic ? "s" : ""));
    }
    for (Member x : c.fields) {
      String d = remap(x.desc, c.name, classMap);
      s.add("f" + x.name + ":" + d);
      s.add("g" + d + (x.isStatic ? "s" : ""));
    }
    if (c.isEnum) s.add("enum");
    return s;
  }

  /** Features of a class plus everything it inherits inside the same jar (a class split into a parent keeps its shape). */
  static Set<String> inheritedFeatures(Cls c, Map<String, Cls> jar, Map<String, String> classMap) {
    Set<String> s = new HashSet<>();
    Deque<String> todo = new ArrayDeque<>(List.of(c.name));
    Set<String> seen = new HashSet<>();
    while (!todo.isEmpty()) {
      Cls k = jar.get(todo.pop());
      if (k == null || !seen.add(k.name)) continue;
      for (String f : features(k, classMap)) if (k == c || f.charAt(0) != 'c') s.add(f);
      if (k.superName != null) todo.push(k.superName);
      k.interfaces.forEach(todo::push);
    }
    return s;
  }

  static boolean isSubtype(String sub, String sup, Map<String, Cls> jar) {
    Deque<String> todo = new ArrayDeque<>(List.of(sub));
    Set<String> seen = new HashSet<>();
    while (!todo.isEmpty()) {
      String n = todo.pop();
      if (n.equals(sup)) return true;
      Cls k = jar.get(n);
      if (k == null || !seen.add(n)) continue;
      if (k.superName != null) todo.push(k.superName);
      k.interfaces.forEach(todo::push);
    }
    return false;
  }

  static double jaccard(Set<String> a, Set<String> b) {
    if (a.isEmpty() && b.isEmpty()) return 0;
    int inter = 0;
    Set<String> small = a.size() <= b.size() ? a : b, big = small == a ? b : a;
    for (String x : small) if (big.contains(x)) inter++;
    return (double) inter / (a.size() + b.size() - inter);
  }

  // ---------- member-name similarity ----------
  static List<String> tokens(String name) {
    List<String> t = new ArrayList<>();
    for (String p : name.split("(?<=[a-z0-9])(?=[A-Z])|_")) if (!p.isEmpty()) t.add(p.toLowerCase(Locale.ROOT));
    return t;
  }

  static int lcs(String a, String b) {
    int[][] dp = new int[a.length() + 1][b.length() + 1];
    for (int i = 1; i <= a.length(); i++)
      for (int j = 1; j <= b.length(); j++)
        dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1) ? dp[i - 1][j - 1] + 1 : Math.max(dp[i - 1][j], dp[i][j - 1]);
    return dp[a.length()][b.length()];
  }

  static String innermost(String n) { String s = simple(n); return s.substring(s.lastIndexOf('$') + 1); }

  static double tokenDice(String a, String b) {
    Set<String> ta = new HashSet<>(tokens(a)), tb = new HashSet<>(tokens(b));
    if (ta.isEmpty() && tb.isEmpty()) return 0;
    Set<String> inter = new HashSet<>(ta);
    inter.retainAll(tb);
    return 2.0 * inter.size() / (ta.size() + tb.size());
  }

  /** 0..1: half longest-common-subsequence ratio (case-insensitive), half camel-case token Dice. */
  static double nameSimilarity(String a, String b) {
    String la = a.toLowerCase(Locale.ROOT), lb = b.toLowerCase(Locale.ROOT);
    double l = 2.0 * lcs(la, lb) / (la.length() + lb.length());
    List<String> ta = tokens(a), tb = tokens(b);
    Set<String> inter = new HashSet<>(ta);
    inter.retainAll(new HashSet<>(tb));
    double d = ta.isEmpty() && tb.isEmpty() ? 0 : 2.0 * inter.size() / (new HashSet<>(ta).size() + new HashSet<>(tb).size());
    return 0.5 * l + 0.5 * d;
  }

  record Pair(Member gone, Member added, double score, String how) {}

  /** Pairs gone and new members of one descriptor group: 1x1 by descriptor, otherwise greedy by name similarity. */
  static List<Pair> pairGroup(List<Member> gone, List<Member> added) {
    List<Pair> out = new ArrayList<>();
    if (gone.size() == 1 && added.size() == 1) {
      out.add(new Pair(gone.get(0), added.get(0), nameSimilarity(gone.get(0).name, added.get(0).name), "desc-unique"));
      return out;
    }
    List<Pair> all = new ArrayList<>();
    for (Member g : gone) for (Member a : added) all.add(new Pair(g, a, nameSimilarity(g.name, a.name), "name-tiebreak"));
    all.sort(Comparator.comparingDouble(Pair::score).reversed().thenComparing(p -> p.gone.name).thenComparing(p -> p.added.name));
    Set<Member> usedG = new HashSet<>(), usedA = new HashSet<>();
    for (int i = 0; i < all.size(); i++) {
      Pair p = all.get(i);
      if (p.score < 0.3) break;
      if (usedG.contains(p.gone) || usedA.contains(p.added)) continue;
      // an equal-score competitor for either side makes the choice arbitrary: skip both sides
      boolean tie = false;
      for (int j = i + 1; j < all.size() && all.get(j).score == p.score; j++) {
        Pair q = all.get(j);
        if ((q.gone == p.gone && !usedA.contains(q.added)) || (q.added == p.added && !usedG.contains(q.gone))) tie = true;
      }
      usedG.add(p.gone);
      usedA.add(p.added);
      if (!tie) out.add(p);
    }
    return out;
  }

  // ---------- main ----------
  public static void main(String[] args) throws Exception {
    Map<String, String> opt = new HashMap<>();
    for (int i = 0; i < args.length; i++) {
      if (!args[i].startsWith("--") || i + 1 >= args.length) { usage("bad argument: " + args[i]); return; }
      opt.put(args[i].substring(2), args[++i]);
    }
    if (!opt.containsKey("old") || !opt.containsKey("new") || !opt.containsKey("out")) { usage("--old, --new and --out are required"); return; }
    List<String> prefixes = Arrays.asList(opt.getOrDefault("prefix", "net/minecraft/,com/mojang/").split(","));
    Map<String, Cls> oldJar = read(Path.of(opt.get("old")), prefixes), newJar = read(Path.of(opt.get("new")), prefixes);
    Path out = Path.of(opt.get("out"));
    Files.createDirectories(out);

    // overrides
    Map<String, String> forcedClasses = new LinkedHashMap<>();
    List<String[]> forcedMembers = new ArrayList<>();
    List<Hint> hints = new ArrayList<>();
    List<Rule> extraRules = new ArrayList<>();
    List<Moved> moves = new ArrayList<>();
    List<String> overrideErrors = new ArrayList<>(), overrideWarnings = new ArrayList<>();
    if (opt.containsKey("overrides")) {
      int ln = 0;
      for (String raw : Files.readAllLines(Path.of(opt.get("overrides")), StandardCharsets.UTF_8)) {
        ln++;
        String t = raw.trim();
        if (t.isEmpty() || t.startsWith("#")) continue;
        String[] f = t.split("\\s+", 2);
        String rest = f.length > 1 ? f[1] : "";
        switch (f[0]) {
          case "class" -> {
            String[] p = rest.split("\\s+");
            if (p.length != 2) { overrideErrors.add("line " + ln + ": class <old> <new>"); continue; }
            String o = p[0].replace('.', '/'), n = p[1].replace('.', '/');
            if (!oldJar.containsKey(o)) overrideErrors.add("line " + ln + ": old class not in old jar: " + p[0]);
            if (!newJar.containsKey(n)) overrideErrors.add("line " + ln + ": new class not in new jar: " + p[1]);
            forcedClasses.put(o, n);
          }
          case "method", "field" -> {
            String[] p = rest.split("\\s+");
            if (p.length != 4) { overrideErrors.add("line " + ln + ": " + f[0] + " <oldOwner> <oldName> <oldDesc|*> <newName>"); continue; }
            // newName '-' = removed without a successor (never paired by the tie-break)
            forcedMembers.add(new String[]{f[0], p[0].replace('.', '/'), p[1], p[2], p[3]});
          }
          case "hint" -> {
            String[] p = rest.split("\\s+", 3);
            if (p.length != 3) { overrideErrors.add("line " + ln + ": hint <owner> <name> <message>"); continue; }
            String o = p[0].replace('.', '/');
            if (!newJar.containsKey(o) && !oldJar.containsKey(o)) overrideErrors.add("line " + ln + ": hint owner in neither jar: " + p[0]);
            hints.add(new Hint(o, p[1], p[2]));
          }
          case "rule" -> {
            String[] p = rest.split("\\s+");
            if (p.length != 3) { overrideErrors.add("line " + ln + ": rule <kind> <old> <new>"); continue; }
            extraRules.add(new Rule(p[0], p[1], p[2]));
          }
          case "moved" -> {
            // moved <owner> <member> <replacement>: owner is the NEW jar's class javac reports as the location
            String[] p = rest.split("\\s+");
            if (p.length != 3) { overrideErrors.add("line " + ln + ": moved <owner> <member> <replacement>"); continue; }
            String o = p[0].replace('.', '/');
            Cls oc = newJar.get(o);
            if (oc == null) { overrideErrors.add("line " + ln + ": moved owner not in new jar: " + p[0]); continue; }
            if (!p[1].matches("[A-Za-z_$][\\w$]*")) { overrideErrors.add("line " + ln + ": moved member must be an identifier: " + p[1]); continue; }
            if (!p[2].matches("[A-Za-z_$][\\w$.()]*")) { overrideErrors.add("line " + ln + ": moved replacement must be an identifier path (a.b, a.b()): " + p[2]); continue; }
            if (oc.methods.stream().anyMatch(x -> x.name.equals(p[1])) || oc.fields.stream().anyMatch(x -> x.name.equals(p[1])))
              overrideWarnings.add("line " + ln + ": moved member " + p[0] + "#" + p[1] + " still exists in the new jar (only rejected call sites are rewritten)");
            moves.add(new Moved(o, p[1], p[2]));
          }
          default -> overrideErrors.add("line " + ln + ": unknown kind '" + f[0] + "'");
        }
      }
    }
    if (!overrideErrors.isEmpty()) {
      System.out.println("ERROR: bad overrides file:");
      overrideErrors.forEach(e -> System.out.println("  " + e));
      System.exit(2);
    }

    List<String> removed = oldJar.keySet().stream().filter(k -> !newJar.containsKey(k)).sorted().toList();
    TreeSet<String> added = new TreeSet<>();
    for (String k : newJar.keySet()) if (!oldJar.containsKey(k)) added.add(k);
    Map<String, String> classMap = new TreeMap<>(), how = new HashMap<>();
    Set<String> claimed = new HashSet<>();
    List<String> warnings = new ArrayList<>();

    for (var e : forcedClasses.entrySet()) { classMap.put(e.getKey(), e.getValue()); how.put(e.getKey(), "override"); claimed.add(e.getValue()); }

    Map<String, Set<String>> newFeat = new HashMap<>();
    for (Cls c : newJar.values()) newFeat.put(c.name, features(c, null));

    // 1. moved: unique same simple name among added classes; a very different shape makes it a suspect instead
    Map<String, List<String>> addedBySimple = new HashMap<>();
    for (String a : added) addedBySimple.computeIfAbsent(simple(a), k -> new ArrayList<>()).add(a);
    Map<String, String> suspects = new TreeMap<>();
    for (String r : removed) {
      List<String> c = addedBySimple.getOrDefault(simple(r), List.of());
      if (c.size() != 1) continue;
      String cand = c.get(0);
      Set<String> of = inheritedFeatures(oldJar.get(r), oldJar, classMap), nf = inheritedFeatures(newJar.get(cand), newJar, null);
      double sim = jaccard(of, nf);
      if (classMap.containsKey(r)) {
        if (!classMap.get(r).equals(cand))
          warnings.add(String.format(Locale.ROOT, "same simple name, different shape: %s -> override %s; same-name %s has similarity %.2f",
            dotted(r), dotted(classMap.get(r)), dotted(cand), sim));
        continue;
      }
      if (claimed.contains(cand)) continue;
      if (sim < 0.2 && of.size() >= 4 && nf.size() >= 4) { suspects.put(r, cand); continue; }
      classMap.put(r, cand);
      how.put(r, String.format(Locale.ROOT, "moved(%.2f)", sim));
      claimed.add(cand);
    }

    // 2. renamed outer classes: inner classes that keep their inner name under a new outer, majority vote
    Map<String, Map<String, Integer>> votes = new TreeMap<>();
    for (String r : removed) {
      if (classMap.containsKey(r) || !isNested(r)) continue;
      String o = outer(r), inner = r.substring(o.length());
      for (String a : added) {
        if (!isNested(a) || !a.endsWith(inner)) continue;
        String aOuter = outer(a);
        if (!oldJar.containsKey(aOuter) && !aOuter.equals(o)) votes.computeIfAbsent(o, k -> new TreeMap<>()).merge(aOuter, 1, Integer::sum);
      }
    }
    votes.forEach((o, v) -> {
      if (classMap.containsKey(o) || !oldJar.containsKey(o) || newJar.containsKey(o)) return;
      var best = v.entrySet().stream().max(Map.Entry.comparingByValue()).get();
      long ties = v.values().stream().filter(x -> x.equals(best.getValue())).count();
      if (ties == 1 && !claimed.contains(best.getKey()) && (best.getValue() >= 2 || pkg(best.getKey()).equals(pkg(o)))) {
        classMap.put(o, best.getKey());
        how.put(o, "inner-classes(" + best.getValue() + ")");
        claimed.add(best.getKey());
      }
    });
    mapNested(removed, newJar, classMap, how, claimed);

    // 3. member similarity: same package, then across packages; repeat while it finds pairs (better descriptors)
    for (int round = 1; round <= 4; round++) {
      int before = classMap.size();
      similarityPass(removed, added, oldJar, newFeat, classMap, how, claimed, true, round);
      similarityPass(removed, added, oldJar, newFeat, classMap, how, claimed, false, round);
      mapNested(removed, newJar, classMap, how, claimed);
      if (classMap.size() == before) break;
    }

    // 4a. suspects get a more lenient search anywhere: a clearly better shape than the same-name class wins
    for (var e : suspects.entrySet()) {
      String r = e.getKey();
      if (classMap.containsKey(r)) continue;
      Set<String> of = features(oldJar.get(r), classMap);
      double simCand = jaccard(of, newFeat.get(e.getValue()));
      String best = null;
      double bs = 0, second = 0;
      for (String a : added) {
        if (claimed.contains(a) || isAnonymousOrLocal(a) || isNested(a) != isNested(r)) continue;
        double s = jaccard(of, newFeat.get(a));
        if (s > bs) { second = bs; bs = s; best = a; } else if (s > second) second = s;
      }
      if (best != null && bs >= 0.35 && bs - second >= 0.15 && bs >= 2 * simCand) {
        classMap.put(r, best);
        how.put(r, String.format(Locale.ROOT, "shape-over-name(%.2f)", bs));
        claimed.add(best);
      }
    }
    mapNested(removed, newJar, classMap, how, claimed);

    // 4b. suspects: mapped elsewhere by shape -> warn; still free -> keep the same-name pair, but warn
    for (var e : suspects.entrySet()) {
      String r = e.getKey(), cand = e.getValue();
      double simCand = jaccard(inheritedFeatures(oldJar.get(r), oldJar, classMap), inheritedFeatures(newJar.get(cand), newJar, null));
      if (classMap.containsKey(r) && isSubtype(cand, classMap.get(r), newJar) && !claimed.contains(cand)) {
        // the class was split: the same-name class extends the shape match (e.g. BlockAndTintGetter -> BlockAndLightGetter)
        String n = classMap.get(r);
        claimed.remove(n);
        classMap.put(r, cand);
        how.put(r, String.format(Locale.ROOT, "moved-split(%.2f)", simCand));
        claimed.add(cand);
        warnings.add(String.format(Locale.ROOT, "same simple name, different shape: %s -> %s kept (similarity %.2f; it extends the shape match %s: split class) - verify",
          dotted(r), dotted(cand), simCand, dotted(n)));
      } else if (classMap.containsKey(r) && classMap.get(r).equals(cand)) {
        // mapped to the same-name class by another rule (e.g. outer-renamed): nothing to warn about
      } else if (classMap.containsKey(r)) {
        String n = classMap.get(r);
        warnings.add(String.format(Locale.ROOT, "same simple name, different shape: %s -> %s (%s), NOT the same-name %s (similarity %.2f)",
          dotted(r), dotted(n), how.get(r), dotted(cand), simCand));
      } else if (!claimed.contains(cand)) {
        classMap.put(r, cand);
        how.put(r, String.format(Locale.ROOT, "moved-low-shape(%.2f)", simCand));
        claimed.add(cand);
        warnings.add(String.format(Locale.ROOT, "same simple name, different shape: %s -> %s kept (similarity %.2f, no better candidate) - verify",
          dotted(r), dotted(cand), simCand));
      } else {
        warnings.add(String.format(Locale.ROOT, "same simple name, different shape: %s unmapped (same-name %s claimed by another class)", dotted(r), dotted(cand)));
      }
    }
    // 5a. same FQN, different class: the old shape now lives in an added class and the old name was reused
    //     (MC 26.1.2: the BlockModel record is CuboidModel, BlockModel is a new interface)
    for (String k : new TreeSet<>(oldJar.keySet())) {
      if (!newJar.containsKey(k) || isAnonymousOrLocal(k) || classMap.containsKey(k)) continue;
      Set<String> of = features(oldJar.get(k), classMap);
      if (of.size() < 4) continue;
      double sNew = jaccard(inheritedFeatures(oldJar.get(k), oldJar, classMap), inheritedFeatures(newJar.get(k), newJar, null));
      if (sNew >= 0.2) continue;
      String best = null;
      double bs = 0, second = 0;
      for (String a : added) {
        if (claimed.contains(a) || isAnonymousOrLocal(a) || isNested(a) != isNested(k)) continue;
        double s = jaccard(of, newFeat.get(a));
        if (s > bs) { second = bs; bs = s; best = a; } else if (s > second) second = s;
      }
      if (best != null && bs >= 0.6 && bs - second >= 0.2 && bs >= 3 * sNew) {
        classMap.put(k, best);
        how.put(k, String.format(Locale.ROOT, "swap(%.2f)", bs));
        claimed.add(best);
        warnings.add(String.format(Locale.ROOT, "same FQN, different class: %s -> %s (similarity %.2f); the new %s is another class (similarity %.2f) - verify",
          dotted(k), dotted(best), bs, simple(k), sNew));
      }
    }

    // 5b. same FQN, very different shape (semantic swap in place, no successor found)
    for (String k : new TreeSet<>(oldJar.keySet())) {
      if (!newJar.containsKey(k) || isAnonymousOrLocal(k) || classMap.containsKey(k)) continue;
      Set<String> of = features(oldJar.get(k), classMap), nf = newFeat.get(k);
      if (of.size() < 6 || nf.size() < 6) continue;
      double s = jaccard(of, nf);
      if (s < 0.05) warnings.add(String.format(Locale.ROOT, "same FQN, different shape: %s (similarity %.2f)", dotted(k), s));
    }

    if (opt.containsKey("explain")) {
      for (String pair : opt.get("explain").split(";")) {
        String[] p = pair.split(",");
        Cls a = oldJar.get(p[0].replace('.', '/')), b = newJar.get(p[1].replace('.', '/'));
        if (a == null || b == null) { System.out.println("explain: class not found: " + pair); continue; }
        Set<String> fa = features(a, classMap), fb = newFeat.get(b.name);
        Set<String> common = new TreeSet<>(fa); common.retainAll(fb);
        Set<String> onlyA = new TreeSet<>(fa); onlyA.removeAll(fb);
        Set<String> onlyB = new TreeSet<>(fb); onlyB.removeAll(fa);
        System.out.printf(Locale.ROOT, "explain %s vs %s: jaccard=%.2f inherited=%.2f classMap=%s%n  common=%s%n  onlyOld=%s%n  onlyNew=%s%n",
          a.name, b.name, jaccard(fa, fb), jaccard(inheritedFeatures(a, oldJar, classMap), inheritedFeatures(b, newJar, null)),
          classMap.get(a.name), common, onlyA, onlyB);
      }
    }

    // ---------- moved static fields + constants folded into collections ----------
    // newStaticFields: name:desc -> new classes where that static field is new (class added, or its old counterpart lacked the name)
    Map<String, String> inverse = new HashMap<>();
    for (String o : oldJar.keySet()) { String n = classMap.getOrDefault(o, o); if (newJar.containsKey(n)) inverse.put(n, o); }
    Map<String, List<String>> newStaticFields = new HashMap<>();
    for (Cls c : newJar.values()) {
      if (isAnonymousOrLocal(c.name)) continue;
      Cls oc = inverse.containsKey(c.name) ? oldJar.get(inverse.get(c.name)) : null;
      Set<String> oldNames = new HashSet<>();
      if (oc != null) oc.fields.forEach(x -> oldNames.add(x.name));
      for (Member x : c.fields) if (x.isStatic && !x.isPrivate && !oldNames.contains(x.name)) newStaticFields.computeIfAbsent(x.name + ":" + x.desc, k -> new ArrayList<>()).add(c.name);
    }
    Map<String, String> collectionRules = collectionRules(oldJar, newJar, classMap, warnings); // oldOwner|NAME -> expression
    List<String[]> fieldMoves = new ArrayList<>(); // oldOwner, name, newOwner
    List<String> changedRows = new ArrayList<>();

    // ---------- member renames ----------
    List<String> memberRows = new ArrayList<>(), removedRows = new ArrayList<>();
    Map<String, String> forcedKey = new HashMap<>(); // kind|owner|name|desc -> newName
    for (String[] fm : forcedMembers) forcedKey.put(fm[0] + "|" + fm[1] + "|" + fm[2] + "|" + fm[3], fm[4]);
    int methodCount = 0, fieldCount = 0;
    for (String on : new TreeSet<>(oldJar.keySet())) {
      String nn = classMap.getOrDefault(on, on);
      Cls oc = oldJar.get(on), nc = newJar.get(nn);
      if (nc == null) { removedRows.add("class\t" + on); continue; }
      for (String kind : List.of("method", "field")) {
        boolean isMethod = kind.equals("method");
        List<Member> om = (isMethod ? oc.methods : oc.fields).stream().filter(x -> !x.isPrivate && !x.name.equals("<init>")).toList();
        List<Member> nm = (isMethod ? nc.methods : nc.fields).stream().filter(x -> !x.isPrivate && !x.name.equals("<init>")).toList();
        Set<String> newKeys = new HashSet<>(), oldKeys = new HashSet<>();
        for (Member x : nm) newKeys.add(isMethod ? x.name + x.desc : x.name);
        for (Member x : om) oldKeys.add(isMethod ? x.name + remap(x.desc, null, classMap) : x.name);
        List<Member> gone = om.stream().filter(x -> !newKeys.contains(isMethod ? x.name + remap(x.desc, null, classMap) : x.name)).toList();
        List<Member> fresh = nm.stream().filter(x -> !oldKeys.contains(isMethod ? x.name + x.desc : x.name)).toList();
        Set<Member> done = new HashSet<>();
        // forced member renames first. A forced row may name a member that still exists (same-name swap, e.g. 26.1
        // RenderTypes.entityCutout -> entityCutoutCull while entityCutoutNoCull took the name entityCutout): the row
        // documents it and reserves the target; the applier never sees an error for it, so callers need a manual grep.
        for (Member g : om) {
          String nName = forcedKey.getOrDefault(kind + "|" + on + "|" + g.name + "|" + g.desc, forcedKey.get(kind + "|" + on + "|" + g.name + "|*"));
          if (nName == null) continue;
          done.add(g);
          if (nName.equals("-")) { removedRows.add(kind + "\t" + on + "\t" + g.name + (isMethod ? g.desc : "") + "\tforced"); continue; }
          String nd = remap(g.desc, null, classMap);
          // a forced target is taken: the name tie-break must not hand it to another gone member as well
          String forcedTarget = nName;
          fresh = fresh.stream().filter(x -> !(x.name.equals(forcedTarget) && (!isMethod || x.desc.equals(nd)))).toList();
          memberRows.add(String.join("\t", kind, on, nn, g.name, nName, g.desc, nd, g.isStatic ? "1" : "0", gone.contains(g) ? "override" : "override-swap"));
          if (isMethod) methodCount++; else fieldCount++;
        }
        if (!isMethod) {
          for (Member g : gone) {
            if (done.contains(g) || !g.isStatic) continue;
            if (collectionRules.containsKey(on + "|" + g.name)) { done.add(g); continue; }
            // moved static field: the same name + descriptor is new in exactly one other class (EntityType.PIG -> EntityTypes.PIG)
            List<String> owners = newStaticFields.getOrDefault(g.name + ":" + remap(g.desc, null, classMap), List.of()).stream().filter(x -> !x.equals(nn)).toList();
            // pulled up into a supertype (Slime.MAX_NATURAL_SIZE -> AbstractCubeMob): old uses still compile, no rule
            if (owners.stream().anyMatch(x -> isSubtype(nn, x, newJar))) { done.add(g); continue; }
            if (owners.size() == 1) { done.add(g); fieldMoves.add(new String[]{on, g.name, owners.get(0)}); }
          }
        }
        // same name, other descriptor (overload swapped / field retyped): compiles at some callers, so the S16 caller review needs it
        Map<String, List<String>> newDescs = new HashMap<>();
        for (Member x : nm) newDescs.computeIfAbsent(x.name, k -> new ArrayList<>()).add(x.desc);
        for (Member g : om) {
          List<String> nds = newDescs.get(g.name);
          if (nds == null || nds.contains(remap(g.desc, null, classMap))) continue;
          if (isMethod && !gone.contains(g)) continue;
          changedRows.add(String.join("\t", kind, on, nn, g.name, g.desc, String.join(" ", nds), g.isStatic ? "1" : "0", "pair"));
        }
        Map<String, List<Member>> goneBy = new TreeMap<>(), freshBy = new HashMap<>();
        for (Member g : gone) if (!done.contains(g)) goneBy.computeIfAbsent(remap(g.desc, null, classMap) + g.isStatic, k -> new ArrayList<>()).add(g);
        for (Member a : fresh) freshBy.computeIfAbsent(a.desc + a.isStatic, k -> new ArrayList<>()).add(a);
        for (var ge : goneBy.entrySet()) {
          List<Member> cands = freshBy.getOrDefault(ge.getKey(), List.of());
          List<Pair> pairs = cands.isEmpty() ? List.of() : pairGroup(ge.getValue(), cands);
          Set<Member> paired = new HashSet<>();
          for (Pair p : pairs) {
            paired.add(p.gone);
            memberRows.add(String.join("\t", kind, on, nn, p.gone.name, p.added.name, p.gone.desc, p.added.desc, p.gone.isStatic ? "1" : "0",
              String.format(Locale.ROOT, "%s(%.2f)", p.how, p.score)));
            if (isMethod) methodCount++; else fieldCount++;
          }
          for (Member g : ge.getValue())
            if (!paired.contains(g)) removedRows.add(kind + "\t" + on + "\t" + g.name + (isMethod ? g.desc : "") + (cands.isEmpty() ? "" : "\tambiguous:" + cands.size()));
        }
      }
    }
    for (Hint h : hints) memberRows.add(String.join("\t", "hint", h.owner, classMap.getOrDefault(h.owner, h.owner), h.name, "-", "-", "-", "-", h.message));
    for (Moved mv : moves) memberRows.add(String.join("\t", "moved", mv.owner, mv.owner, mv.member, mv.replacement, "-", "-", "-", "moved"));
    // same FQN, another class (MC 26.2: the old Gui is Hud, Gui is new): old callers of a name both classes have still compile
    for (var e : classMap.entrySet()) {
      Cls reused = newJar.get(e.getKey());
      if (reused == null || isAnonymousOrLocal(e.getKey())) continue;
      Cls oc = oldJar.get(e.getKey());
      for (String kind : List.of("method", "field")) {
        boolean isMethod = kind.equals("method");
        Map<String, List<String>> nd = new TreeMap<>();
        for (Member x : isMethod ? reused.methods : reused.fields) if (!x.isPrivate && !x.name.equals("<init>")) nd.computeIfAbsent(x.name, k -> new ArrayList<>()).add(x.desc);
        Set<String> seen = new HashSet<>();
        for (Member g : isMethod ? oc.methods : oc.fields)
          if (!g.isPrivate && !g.name.equals("<init>") && nd.containsKey(g.name) && seen.add(g.name + g.desc))
            changedRows.add(String.join("\t", kind, e.getKey(), e.getKey(), g.name, g.desc, String.join(" ", nd.get(g.name)), g.isStatic ? "1" : "0",
              "fqn-reuse(old class -> " + dotted(e.getValue()) + ")"));
      }
    }
    Collections.sort(removedRows);
    Collections.sort(changedRows);

    // moved static fields: a class receiving >= 2 fields of the old owner, or sharing a name token with it, is trusted;
    // a single field landing in an unrelated class (a generic CODEC) is listed commented out for review
    Map<String, Integer> moveGroups = new HashMap<>();
    for (String[] m : fieldMoves) moveGroups.merge(m[0] + "|" + m[2], 1, Integer::sum);
    List<String> fieldRules = new ArrayList<>(), weakFieldRules = new ArrayList<>(), collectionLines = new ArrayList<>();
    for (String[] m : fieldMoves) {
      String line = "field " + dotted(m[0]) + "." + m[1] + " " + dotted(m[2]) + "." + m[1];
      if (moveGroups.get(m[0] + "|" + m[2]) >= 2 || tokenDice(innermost(m[0]), innermost(m[2])) > 0) fieldRules.add(line);
      else weakFieldRules.add("# weak (single field, unrelated class; uncomment after review): " + line);
    }
    for (var e : collectionRules.entrySet()) {
      String[] k = e.getKey().split("\\|");
      collectionLines.add("field " + dotted(k[0]) + "." + k[1] + " " + e.getValue());
    }

    // ---------- map.txt ----------
    List<String> nestedRules = new ArrayList<>(), renamedRules = new ArrayList<>(), movedRules = new ArrayList<>(), forcedRules = new ArrayList<>();
    for (var e : classMap.entrySet()) {
      String o = e.getKey(), n = e.getValue();
      if (isAnonymousOrLocal(o) || isAnonymousOrLocal(n)) continue;
      String line = "class " + dotted(o) + " " + dotted(n);
      if (isNested(o)) {
        String implied = classMap.getOrDefault(outer(o), outer(o)) + o.substring(outer(o).length());
        if (!implied.equals(n)) nestedRules.add(line);
        continue;
      }
      if ("override".equals(how.get(o))) forcedRules.add(line);
      else if (!simple(o).equals(simple(n))) renamedRules.add(line);
      else movedRules.add(line);
    }
    String title = opt.getOrDefault("title", "old jar -> new jar");
    List<String> map = new ArrayList<>();
    map.add("# Rename map " + title + " - GENERATED by FindApiRenames.java (Find-ApiRenames.ps1); do not edit by hand,");
    map.add("# edit the overrides file and regenerate. Format: see Invoke-RenameMap.ps1 / README.md.");
    map.add("# class <old> <new>: FQN everywhere; simple name too in files that import the old class ($ = nested class).");
    map.add("# Member renames are NOT here: they are receiver-dependent and go through Invoke-CompilerRenames.ps1.");
    map.add("# field <oldOwner>.<NAME> <expression>: moved static fields / constants folded into collections; they run first,");
    map.add("# while the old owner's import is still there (the class rules below may rename it).");
    map.add("");
    addSection(map, "moved static fields (same name + descriptor, new in exactly one other class)", fieldRules);
    if (!weakFieldRules.isEmpty()) addSection(map, "moved static fields to review (not applied)", weakFieldRules);
    addSection(map, "constants folded into collections (ColorCollection#pick, WeatheringCopperCollection accessors)", collectionLines);
    addSection(map, "nested classes renamed independently of their outer class (must run before their outer rule)", nestedRules);
    addSection(map, "manual overrides (overrides file)", forcedRules);
    addSection(map, "renamed classes (different simple name; matched by inner classes or member similarity)", renamedRules);
    addSection(map, "moved classes (same simple name, new package)", movedRules);
    List<String> extra = extraRules.stream().map(r -> r.kind + " " + r.old + " " + r.replacement).toList();
    addSection(map, "extra rules (overrides file)", extra);

    Files.write(out.resolve("classes.tsv"), classMap.entrySet().stream().map(e -> e.getKey() + "\t" + e.getValue() + "\t" + how.get(e.getKey())).toList());
    List<String> members = new ArrayList<>();
    members.add("# GENERATED by FindApiRenames.java (" + title + "). kind\toldOwner\tnewOwner\toldName\tnewName\toldDesc\tnewDesc\tstatic\thow");
    members.addAll(memberRows);
    Files.write(out.resolve("members.tsv"), members);
    Files.write(out.resolve("removed.tsv"), removedRows);
    List<String> changed = new ArrayList<>();
    changed.add("# same name, other descriptor (S16 caller review). kind\toldOwner\tnewOwner\tname\toldDesc\tnewDescs\tstatic\thow");
    changed.addAll(changedRows);
    Files.write(out.resolve("changed.tsv"), changed);
    warnings.addAll(overrideWarnings);
    Files.write(out.resolve("warnings.txt"), warnings);
    Files.write(out.resolve("map.txt"), map);
    long renamedCount = classMap.entrySet().stream().filter(e -> !simple(e.getKey()).equals(simple(e.getValue()))).count();
    System.out.printf(Locale.ROOT, "old=%d new=%d removedClasses=%d addedClasses=%d classPairs=%d (renamed=%d moved=%d) mapRules=%d methodRenames=%d fieldRenames=%d fieldMoves=%d (weak=%d) collectionRules=%d hints=%d moved=%d changed=%d warnings=%d unmatched=%d%n",
      oldJar.size(), newJar.size(), removed.size(), added.size(), classMap.size(), renamedCount, classMap.size() - renamedCount,
      nestedRules.size() + forcedRules.size() + renamedRules.size() + movedRules.size() + extra.size() + fieldRules.size() + collectionLines.size(),
      methodCount, fieldCount, fieldRules.size(), weakFieldRules.size(), collectionLines.size(), hints.size(), moves.size(), changedRows.size(), warnings.size(), removedRows.size());
    for (String w : warnings) System.out.println("WARNING: " + w);
  }

  /**
   * Constants folded into collections (MC 26.2): {@code Items.RED_DYE} -> {@code Items.DYE.pick(DyeColor.RED)} for every
   * new static {@code ColorCollection} field whose 16 {@code <COLOR>_<X>} (or {@code DYED_<X>}) old fields are gone, and
   * {@code Items.EXPOSED_CUT_COPPER} -> {@code Items.CUT_COPPER.weathering().exposed()} for every new static
   * {@code WeatheringCopperCollection} field whose 8 old fields ({@code [WAXED_][EXPOSED_|WEATHERED_|OXIDIZED_]<X>}, the
   * unaffected one may keep the collection's name: {@code COPPER_BLOCK}) existed. Incomplete families are warnings.
   */
  static Map<String, String> collectionRules(Map<String, Cls> oldJar, Map<String, Cls> newJar, Map<String, String> classMap, List<String> warnings) {
    Map<String, String> out = new TreeMap<>();
    String dye = newJar.keySet().stream().filter(k -> simple(k).equals("DyeColor")).findFirst().orElse(null);
    for (String on : new TreeSet<>(oldJar.keySet())) {
      String nn = classMap.getOrDefault(on, on);
      Cls oc = oldJar.get(on), nc = newJar.get(nn);
      if (nc == null) continue;
      Map<String, Member> oldStatic = new HashMap<>();
      for (Member x : oc.fields) if (x.isStatic && !x.isPrivate) oldStatic.put(x.name, x);
      Set<String> newNames = new HashSet<>();
      nc.fields.forEach(x -> newNames.add(x.name));
      for (Member n : nc.fields) {
        if (!n.isStatic || n.isPrivate) continue;
        String type = n.desc.startsWith("L") ? simple(n.desc.substring(1, n.desc.length() - 1)) : "";
        String owner = dotted(nn) + "." + n.name;
        if (type.equals("ColorCollection") && dye != null) {
          boolean found = false;
          for (String suffix : n.name.startsWith("DYED_") ? List.of(n.name, n.name.substring(5)) : List.of(n.name)) {
            Map<String, String> fam = new LinkedHashMap<>();
            for (String c : DYE_COLORS) if (oldStatic.containsKey(c + "_" + suffix) && !newNames.contains(c + "_" + suffix)) fam.put(c + "_" + suffix, c);
            if (fam.size() != DYE_COLORS.length) continue;
            fam.forEach((old, c) -> out.put(on + "|" + old, owner + ".pick(" + dotted(dye) + "." + c + ")"));
            found = true;
            break;
          }
          if (!found) warnings.add("collection without a complete old color family: " + owner);
        } else if (type.equals("WeatheringCopperCollection")) {
          List<String> bases = n.name.endsWith("_BLOCK") ? List.of(n.name, n.name.substring(0, n.name.length() - 6)) : List.of(n.name);
          Map<String, String> fam = new LinkedHashMap<>();
          String[][] states = {{"unaffected", ""}, {"exposed", "EXPOSED_"}, {"weathered", "WEATHERED_"}, {"oxidized", "OXIDIZED_"}};
          for (String[] w : new String[][]{{"weathering", ""}, {"waxed", "WAXED_"}}) {
            for (String[] s : states) {
              List<String> cands = new ArrayList<>();
              for (String b : bases) cands.add(w[1] + s[1] + b);
              for (String old : cands) {
                // gone, or the collection kept the old name (COPPER_BLOCK: Item -> WeatheringCopperCollection<Item>)
                if (oldStatic.containsKey(old) && (!newNames.contains(old) || old.equals(n.name))) { fam.put(old, "." + w[0] + "()." + s[0] + "()"); break; }
              }
            }
          }
          if (fam.size() != 8) {
            Member same = oldStatic.get(n.name);
            warnings.add("collection without a complete old copper family (" + fam.size() + "/8): " + owner
              + (same != null ? " (the old field of that name was a " + simple(same.desc.replace(";", "")) + ": its accessor calls break, fix by hand)" : ""));
            continue;
          }
          fam.forEach((old, acc) -> out.put(on + "|" + old, owner + acc));
        }
      }
    }
    return out;
  }

  static void addSection(List<String> map, String title, List<String> rules) {
    if (rules.isEmpty()) return;
    map.add("# --- " + title + " ---");
    rules.stream().sorted().forEach(map::add);
    map.add("");
  }

  static void mapNested(List<String> removed, Map<String, Cls> newJar, Map<String, String> classMap, Map<String, String> how, Set<String> claimed) {
    // an unmapped removed outer class whose mapped nested classes all moved under one new, unclaimed outer follows them
    Map<String, Set<String>> outersOfNested = new TreeMap<>();
    for (String r : removed) {
      if (!isNested(r) || !classMap.containsKey(r)) continue;
      String n = classMap.get(r);
      if (!isNested(n) || !n.substring(n.indexOf('$')).equals(r.substring(r.indexOf('$')))) continue;
      outersOfNested.computeIfAbsent(outer(r), k -> new TreeSet<>()).add(outer(n));
    }
    Set<String> removedSet = new HashSet<>(removed);
    outersOfNested.forEach((o, ns) -> {
      if (ns.size() != 1 || classMap.containsKey(o) || !removedSet.contains(o)) return;
      String n = ns.iterator().next();
      if (claimed.contains(n) || !newJar.containsKey(n)) return;
      classMap.put(o, n);
      how.put(o, "nested-classes");
      claimed.add(n);
    });
    for (String r : removed) {
      if (classMap.containsKey(r) || !isNested(r)) continue;
      String o = outer(r);
      if (!classMap.containsKey(o)) continue;
      String cand = classMap.get(o) + r.substring(o.length());
      if (newJar.containsKey(cand) && !claimed.contains(cand)) { classMap.put(r, cand); how.put(r, "outer-renamed"); claimed.add(cand); }
    }
  }

  static void similarityPass(List<String> removed, Set<String> added, Map<String, Cls> oldJar, Map<String, Set<String>> newFeat,
                             Map<String, String> classMap, Map<String, String> how, Set<String> claimed, boolean samePackage, int round) {
    double threshold = samePackage ? 0.5 : 0.55, margin = samePackage ? 0.2 : 0.2;
    record Proposal(String old, String cand, double score) {}
    List<Proposal> proposals = new ArrayList<>();
    for (String r : removed) {
      if (classMap.containsKey(r) || isAnonymousOrLocal(r)) continue;
      Set<String> of = features(oldJar.get(r), classMap);
      if (of.size() < (samePackage ? 3 : 4)) continue;
      // rank by member similarity plus a small class-name bonus; accept on member similarity alone, or on a weaker
      // member similarity when the simple names are close (BlockModelPart -> BlockStateModelPart)
      String best = null;
      double bs = 0, second = 0, bestJ = 0, bestName = 0;
      for (String a : added) {
        if (claimed.contains(a) || isAnonymousOrLocal(a) || isNested(a) != isNested(r)) continue;
        if (samePackage && !pkg(a).equals(pkg(r))) continue;
        double j = jaccard(of, newFeat.get(a));
        if (j < 0.2) continue;
        double ns = tokenDice(innermost(r), innermost(a));
        double s = j + 0.3 * ns;
        if (s > bs) { second = bs; bs = s; best = a; bestJ = j; bestName = ns; } else if (s > second) second = s;
      }
      boolean ok = best != null && bs - second >= margin && (bestJ >= threshold || (bestJ >= 0.3 && bestName >= 0.75 && !isNested(r)));
      if (ok) proposals.add(new Proposal(r, best, bestJ));
    }
    proposals.sort(Comparator.comparingDouble(Proposal::score).reversed().thenComparing(Proposal::old));
    for (Proposal p : proposals) {
      if (claimed.contains(p.cand) || classMap.containsKey(p.old)) continue;
      classMap.put(p.old, p.cand);
      how.put(p.old, String.format(Locale.ROOT, "%s(%.2f,r%d)", samePackage ? "similarity" : "similarity-xpkg", p.score, round));
      claimed.add(p.cand);
    }
  }

  static void usage(String error) {
    System.out.println("ERROR: " + error);
    System.out.println("usage: java FindApiRenames.java --old OLD.jar --new NEW.jar --out DIR [--overrides FILE] [--prefix p1,p2] [--title TEXT]");
    System.exit(1);
  }
}
