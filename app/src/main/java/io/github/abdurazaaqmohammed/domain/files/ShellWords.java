package io.github.abdurazaaqmohammed.domain.files;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Arguments are data, never evaluated as another shell command. */
public final class ShellWords {
    private ShellWords() { }
    public static String quote(String value) { return "'"+value.replace("'","'\\''")+"'"; }
    public static List<String> parse(String value) throws IOException {
        List<String> result=new ArrayList<>(); StringBuilder word=new StringBuilder();
        char quote=0; boolean escaped=false, present=false;
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if (escaped) { word.append(c); escaped=false; present=true; }
            else if (c=='\\' && quote!='\'') { escaped=true; present=true; }
            else if (quote!=0) { if (c==quote) quote=0; else word.append(c); present=true; }
            else if (c=='\'' || c=='"') { quote=c; present=true; }
            else if (Character.isWhitespace(c)) { if (present) { result.add(word.toString()); word.setLength(0); present=false; } }
            else { word.append(c); present=true; }
        }
        if (escaped || quote!=0) throw new IOException("Close the argument's quote or escape.");
        if (present) result.add(word.toString()); return result;
    }
}
