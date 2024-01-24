package org.jboss.set.channelreports.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

public final class VersionUtils {

    private static final String DELIMITER_REGEX = "[-._]";

    private VersionUtils() {
    }

    /**
     * Splits the version string into segments. Delimiters are '-', '.' and '_'.
     *
     * @param version version string
     * @return segments of the version string
     */
    public static String[] parseVersion(String version) {
        return version.split(DELIMITER_REGEX);
    }

    /**
     * Returns a starting sequence of numerical segments.
     *
     * @param segments version string segments
     * @return numerical segments
     */
    public static String[] numericalSegments(String[] segments) {
        for (int i = 0; i < segments.length; i++) {
            try {
                Integer.valueOf(segments[i]);
            } catch (NumberFormatException e) {
                return Arrays.copyOf(segments, i);
            }
        }
        return segments;
    }

    /**
     * Returns the remainder of the version string after the numerical segments.
     *
     * @param version version string
     * @return a qualifier portion of the version string or an empty string
     */
    public static String qualifier(String version) {
        String previousRemainder = version;
        String[] segments;
        while (true) {
            segments = previousRemainder.split(DELIMITER_REGEX, 2);
            try {
                Integer.valueOf(segments[0]);
            } catch (NumberFormatException e) {
                return previousRemainder;
            }
            if (segments.length == 1) {
                break;
            }
            previousRemainder = segments[1];
        }
        return "";
    }

    /**
     * Returns the first non-numeric segment.
     *
     * @param segments version segments
     * @return the first non-numeric segment or empty string
     */
    public static String firstQualifierSegment(String[] segments) {
        for (String segment : segments) {
            try {
                Integer.valueOf(segment);
            } catch (NumberFormatException e) {
                return segment;
            }
        }
        return "";
    }

    public static String firstQualifierSegment(String version) {
        return firstQualifierSegment(parseVersion(version));
    }

    /**
     * Checks if the two versions belong to the same minor stream.
     * <p>
     * Examples:
     * <li>"1.2.3" and "1.2.4" should return true,</li>
     * <li>"1.2.3" and "1.3.0" should return false.</li>
     *
     * @param v1 first version
     * @param v2 second version
     * @return belong to the same minor stream?
     */
    public static boolean isTheSameMinor(String v1, String v2) {
        String[] s1 = parseVersion(v1);
        String[] s2 = parseVersion(v2);
        for (int i = 0; i < 2; i++) {
            if (i >= s1.length || i >= s2.length || !s1[i].equals(s2[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds a highest version from given list that belongs to the same minor stream as the given base version.
     *
     * @param baseVersion base version string
     * @param upgradeVersions upgrade version strings, must be ordered from lowest to highest
     * @return the highest available micro upgrade
     */
    public static Optional<String> findMicroUpgrade(String baseVersion, Collection<String> upgradeVersions) {
        Optional<String> result = Optional.empty();
        for (String version: upgradeVersions) {
            if (isTheSameMinor(baseVersion, version)) {
                result = Optional.of(version);
            }
        }
        return result;
    }

}
