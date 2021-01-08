package net.corda.flask.common;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;

public class ManifestEscape {

    public static List<String> splitManifestStringList(String s) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        CharacterIterator it = new StringCharacterIterator(s);
        for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
            if (escape) {
                escape = false;
                switch (c) {
                    case '"':
                        sb.append('"');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case ' ':
                        sb.append(' ');
                        break;
                    default:
                        throw new StringParseException(String.format("Unrecognized escape sequence '\\%c'", c));
                }
            } else if(c == ' ') {
                result.add(sb.toString());
                sb = new StringBuilder();
            } else if (c == '\\') {
                escape = true;
            } else {
                sb.append(c);
            }
        }
        if(sb.length() > 0) result.add(sb.toString());
        return result;
    }

    public static String escapeStringList(List<String> strings){
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while(i < strings.size()) {
            CharacterIterator it = new StringCharacterIterator(strings.get(i));
            for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
                switch (c) {
                    case '"':
                        sb.append("\\\"");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case ' ':
                        sb.append("\\ ");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    default:
                        sb.append(c);
                        break;
                }
            }
            if(++i < strings.size()) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
