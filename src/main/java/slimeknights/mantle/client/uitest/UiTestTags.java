package slimeknights.mantle.client.uitest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The known scenario tags ({@link UiTestScenario#tags()}), selected with {@code -Dmantle.uitest.tags=<tag,...>}. Mantle defines the
 * shared vocabulary here; a mod adds its own area tags with {@link #register(String, String)}, e.g. from a static constant of its
 * scenario class, so they exist before the suite checks the registered scenarios. Keep the vocabulary small: a tag names an area worth
 * running on its own while iterating on a fix.
 */
public final class UiTestTags {
  /** Tag name to its description, in registration order */
  private static final Map<String, String> KNOWN = new LinkedHashMap<>();
  private static final Pattern NAME = Pattern.compile("[a-z0-9_]+");

  /** Quick cross-section for a fast sanity run: boot, a station GUI, an in-world render, JEI, the book and the suite self-checks. */
  public static final String SMOKE = register("smoke", "quick cross-section: boot, a station GUI, an in-world render, JEI, the book, the suite self-checks");
  /** Checks of the uitest suite itself (input isolation, progress display). */
  public static final String SUITE = register("suite", "checks of the uitest suite itself (input isolation, progress display)");
  /** Captures or checks a screen (container menu, inventory, custom screen). */
  public static final String GUI = register("gui", "captures or checks a screen");
  /** Captures the world with no screen open (camera aimed at a rig, HUD). */
  public static final String WORLD = register("world", "captures the world with no screen open (rigs, HUD)");
  /** Model, texture, atlas or render type checks (block, item and entity rendering). */
  public static final String RENDER = register("render", "model, texture, atlas or render type checks");
  /** Fluid rendering or fluid handling (tanks, casting, fluid blocks). */
  public static final String FLUID = register("fluid", "fluid rendering or handling (tanks, casting, fluid blocks)");
  /** JEI integration (categories, recipe transfer, ingredient lookups). */
  public static final String JEI = register("jei", "JEI integration (categories, transfer, lookups)");
  /** Mantle's book system (rendering, structures, export). */
  public static final String BOOK = register("book", "Mantle's book system (pages, structures, export)");
  /** Integration with a third party mod other than JEI (e.g. Crafting Tweaks). */
  public static final String COMPAT = register("compat", "integration with another third party mod (e.g. Crafting Tweaks)");

  private UiTestTags() {}

  /**
   * Registers a known tag; registering the same tag again keeps the first description.
   * @param tag          Tag name, lowercase letters, digits and underscores
   * @param description  One line description for logs and docs
   * @return the tag, for use as a constant
   */
  public static synchronized String register(String tag, String description) {
    if (!NAME.matcher(tag).matches()) {
      throw new IllegalArgumentException("uitest: invalid tag name '" + tag + "', use lowercase letters, digits and underscores");
    }
    KNOWN.putIfAbsent(tag, description);
    return tag;
  }

  /** Every registered tag, sorted */
  public static synchronized Set<String> known() {
    return Collections.unmodifiableSortedSet(new TreeSet<>(KNOWN.keySet()));
  }

  /** Description of a registered tag, or null if unknown */
  public static synchronized String description(String tag) {
    return KNOWN.get(tag);
  }

  /** Problems with the tags of a scenario, empty when it has at least one tag and only known tags. */
  public static List<String> problems(Set<String> tags, Set<String> known) {
    List<String> problems = new ArrayList<>();
    if (tags.isEmpty()) {
      problems.add("no tags");
    }
    for (String tag : tags) {
      if (!known.contains(tag)) {
        problems.add("unknown tag '" + tag + "'");
      }
    }
    return problems;
  }
}
