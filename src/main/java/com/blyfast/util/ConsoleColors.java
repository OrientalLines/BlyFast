package com.blyfast.util;

/** Utility class for ANSI color codes to make console output more colorful. */
public class ConsoleColors {
  // Reset
  public static final String RESET = "\033[0m";

  // Regular Colors
  public static final String BLACK = "\033[0;30m";
  public static final String RED = "\033[0;31m";
  public static final String GREEN = "\033[0;32m";
  public static final String YELLOW = "\033[0;33m";
  public static final String BLUE = "\033[0;34m";
  public static final String PURPLE = "\033[0;35m";
  public static final String CYAN = "\033[0;36m";
  public static final String WHITE = "\033[0;37m";

  // Bold
  public static final String BLACK_BOLD = "\033[1;30m";
  public static final String RED_BOLD = "\033[1;31m";
  public static final String GREEN_BOLD = "\033[1;32m";
  public static final String YELLOW_BOLD = "\033[1;33m";
  public static final String BLUE_BOLD = "\033[1;34m";
  public static final String PURPLE_BOLD = "\033[1;35m";
  public static final String CYAN_BOLD = "\033[1;36m";
  public static final String WHITE_BOLD = "\033[1;37m";

  // Background
  public static final String BLACK_BACKGROUND = "\033[40m";
  public static final String RED_BACKGROUND = "\033[41m";
  public static final String GREEN_BACKGROUND = "\033[42m";
  public static final String YELLOW_BACKGROUND = "\033[43m";
  public static final String BLUE_BACKGROUND = "\033[44m";
  public static final String PURPLE_BACKGROUND = "\033[45m";
  public static final String CYAN_BACKGROUND = "\033[46m";
  public static final String WHITE_BACKGROUND = "\033[47m";

  /**
   * Wraps text with the specified color and resets it after
   *
   * @param text the text to color
   * @param color the ANSI color code
   * @return the colored text
   */
  public static String colored(String text, String color) {
    return color + text + RESET;
  }

  /**
   * Wraps text in bold with the specified color
   *
   * @param text the text to format
   * @param color the ANSI color code
   * @return the bold colored text
   */
  public static String bold(String text, String color) {
    String boldColor = color.replace("[0;", "[1;");
    return boldColor + text + RESET;
  }
}
