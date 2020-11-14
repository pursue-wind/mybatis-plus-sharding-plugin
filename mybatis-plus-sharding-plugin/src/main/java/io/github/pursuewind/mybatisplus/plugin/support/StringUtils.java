package io.github.pursuewind.mybatisplus.plugin.support;


/**
 * @author Mireal
 * @version V1.0
 * @date 2020/11/15 1:18
 */
public class StringUtils {
    public static String replaceOnce(String text, String searchString, String replacement) {
        return replace(text, searchString, replacement, 1);
    }

    public static String replace(String text, String searchString, String replacement, int max) {
        return replace(text, searchString, replacement, max, false);
    }

    static int indexOf(CharSequence cs, CharSequence searchChar, int start) {
        if (cs instanceof String) {
            return ((String) cs).indexOf(searchChar.toString(), start);
        } else if (cs instanceof StringBuilder) {
            return ((StringBuilder) cs).indexOf(searchChar.toString(), start);
        } else {
            return cs instanceof StringBuffer ? ((StringBuffer) cs).indexOf(searchChar.toString(), start) : cs.toString().indexOf(searchChar.toString(), start);
        }
    }

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public static int indexOfIgnoreCase(CharSequence str, CharSequence searchStr, int startPos) {
        if (str != null && searchStr != null) {
            if (startPos < 0) {
                startPos = 0;
            }

            int endLimit = str.length() - searchStr.length() + 1;
            if (startPos > endLimit) {
                return -1;
            } else if (searchStr.length() == 0) {
                return startPos;
            } else {
                for (int i = startPos; i < endLimit; ++i) {
                    if (regionMatches(str, true, i, searchStr, 0, searchStr.length())) {
                        return i;
                    }
                }

                return -1;
            }
        } else {
            return -1;
        }
    }

    static boolean regionMatches(CharSequence cs, boolean ignoreCase, int thisStart, CharSequence substring, int start, int length) {
        if (cs instanceof String && substring instanceof String) {
            return ((String) cs).regionMatches(ignoreCase, thisStart, (String) substring, start, length);
        } else {
            int index1 = thisStart;
            int index2 = start;
            int tmpLen = length;
            int srcLen = cs.length() - thisStart;
            int otherLen = substring.length() - start;
            if (thisStart >= 0 && start >= 0 && length >= 0) {
                if (srcLen >= length && otherLen >= length) {
                    while (tmpLen-- > 0) {
                        char c1 = cs.charAt(index1++);
                        char c2 = substring.charAt(index2++);
                        if (c1 != c2) {
                            if (!ignoreCase) {
                                return false;
                            }
                            char u1 = Character.toUpperCase(c1);
                            char u2 = Character.toUpperCase(c2);
                            if (u1 != u2 && Character.toLowerCase(u1) != Character.toLowerCase(u2)) {
                                return false;
                            }
                        }
                    }

                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    private static String replace(String text, String searchString, String replacement, int max, boolean ignoreCase) {
        if (!isEmpty(text) && !isEmpty(searchString) && replacement != null && max != 0) {
            if (ignoreCase) {
                searchString = searchString.toLowerCase();
            }

            int start = 0;
            int end = ignoreCase ? indexOfIgnoreCase(text, searchString, start) : indexOf(text, searchString, start);
            if (end == -1) {
                return text;
            } else {
                int replLength = searchString.length();
                int increase = Math.max(replacement.length() - replLength, 0);
                increase *= max < 0 ? 16 : Math.min(max, 64);

                StringBuilder buf;
                for (buf = new StringBuilder(text.length() + increase); end != -1; end = ignoreCase ? indexOfIgnoreCase(text, searchString, start) : indexOf(text, searchString, start)) {
                    buf.append(text, start, end).append(replacement);
                    start = end + replLength;
                    --max;
                    if (max == 0) {
                        break;
                    }
                }
                buf.append(text, start, text.length());
                return buf.toString();
            }
        } else {
            return text;
        }
    }
}
