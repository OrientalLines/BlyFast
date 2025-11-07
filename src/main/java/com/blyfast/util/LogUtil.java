package com.blyfast.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Utility class for enhancing log messages with color coding. */
public class LogUtil {

  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

  /**
   * Creates a formatted debug log message with appropriate colors
   *
   * @param message the log message
   * @return the formatted and colored log message
   */
  public static String debug(String message) {
    return formatLog("DEBUG", ConsoleColors.CYAN, message);
  }

  /**
   * Creates a formatted info log message with appropriate colors
   *
   * @param message the log message
   * @return the formatted and colored log message
   */
  public static String info(String message) {
    return formatLog("INFO", ConsoleColors.GREEN, message);
  }

  /**
   * Creates a formatted warning log message with appropriate colors
   *
   * @param message the log message
   * @return the formatted and colored log message
   */
  public static String warn(String message) {
    return formatLog("WARN", ConsoleColors.YELLOW, message);
  }

  /**
   * Creates a formatted error log message with appropriate colors
   *
   * @param message the log message
   * @return the formatted and colored log message
   */
  public static String error(String message) {
    return formatLog("ERROR", ConsoleColors.RED, message);
  }

  /**
   * Formats a log message with timestamp, level, and appropriate colors
   *
   * @param level the log level
   * @param color the color for the log level
   * @param message the message to log
   * @return the formatted and colored log message
   */
  private static String formatLog(String level, String color, String message) {
    String time = LocalDateTime.now().format(TIME_FORMATTER);
    StringBuilder sb = new StringBuilder();

    // Format: [TIME] [LEVEL] message
    sb.append(ConsoleColors.WHITE)
        .append("[")
        .append(time)
        .append("]")
        .append(ConsoleColors.RESET)
        .append(" ")
        .append(color)
        .append("[")
        .append(level)
        .append("]")
        .append(ConsoleColors.RESET)
        .append(" ")
        .append(message);

    return sb.toString();
  }
}
