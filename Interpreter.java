import java.util.*;
import java.io.*;

class TokenType {
    public final static int 
        NUM = 0,
        VAR = 1,
        SYM = 2,
        PNC = 3,
        LBR = 4,
        RBR = 5;
}

class TokenBase {
    public final int type;
    public TokenBase(int t) { type = t; }
    public String toString() {
        return String.valueOf((char)type);
    }
}

class NumberToken extends TokenBase {
    public final int val;
    public NumberToken(int v) {
        super(TokenType.NUM);
        val = v;
    }
    public String toString() {
        return Integer.toString(val);
    }
}

class VarToken extends TokenBase {
    public final String name;
    public VarToken(String s) {
        super(TokenType.VAR);
        name = s;
    }
    public String toString() {
        return name;
    }
}

class SymbolToken extends TokenBase {
    public final char op;
    public SymbolToken(char name) {
        super(TokenType.SYM);
        this.op = name;
    }
    public String toString() {
        return Character.toString(op);
    }
}

class PunctToken extends TokenBase {
    public final char mark;
    public PunctToken(char name) {
        super(TokenType.PNC);
        this.mark = name;
    }
    public String toString() {
        return Character.toString(mark);
    }
}

class BraceToken extends TokenBase {
    public final char brace;
    public BraceToken(char type, int tag) {
        super(tag);
        this.brace = type;
    }
    public String toString() {
        return Character.toString(brace);
    }
}

class Tokenizer {
    public int currentLine = 1;
    private char look = ' ';
    private Hashtable<String, TokenBase> tokens = new Hashtable<>();
    private InputStream source;
    private TokenBase nextToken;
    
    public Tokenizer(InputStream src) {
        this.source = src;
    }
    
    public TokenBase getToken() throws IOException {
        while (Character.isWhitespace(look)) {
            if (look == '\n') currentLine++;
            look = (char)source.read();
        }
        
        if (Character.isDigit(look)) {
            int n = 0;
            do {
                n = 10 * n + (look - '0');
                look = (char)source.read();
            } while (Character.isDigit(look));
            
            if (n == 0 && look != ' ' && look != '\t' && look != '\n' && 
                look != ';' && look != '+' && look != '-' && look != '*' && 
                look != ')' && look != (char)-1) {
                throw new IOException("Invalid number format at line " + currentLine);
            }
            
            return new NumberToken(n);
        }
        
        if (Character.isLetter(look) || look == '_') {
            StringBuilder b = new StringBuilder();
            do {
                b.append(look);
                look = (char)source.read();
            } while (Character.isLetterOrDigit(look) || look == '_');
            
            String s = b.toString();
            TokenBase t = tokens.get(s);
            if (t == null) {
                t = new VarToken(s);
                tokens.put(s, t);
            }
            return t;
        }
        
        TokenBase t;
        switch (look) {
            case '=':
            case '+':
            case '-':
            case '*':
                t = new SymbolToken(look);
                look = ' ';
                return t;
            case ';':
                t = new PunctToken(look);
                look = ' ';
                return t;
            case '(':
                t = new BraceToken(look, TokenType.LBR);
                look = ' ';
                return t;
            case ')':
                t = new BraceToken(look, TokenType.RBR);
                look = ' ';
                return t;
            case (char)-1:
                return null;
            default:
                throw new IOException("Unexpected character at line " + currentLine);
        }
    }
    
    public TokenBase peekToken() throws IOException {
        if (nextToken == null) {
            nextToken = getToken();
        }
        return nextToken;
    }
    
    public TokenBase nextToken() throws IOException {
        if (nextToken != null) {
            TokenBase t = nextToken;
            nextToken = null;
            return t;
        }
        return getToken();
    }
}

public class ProgramExecutor {
    private Map<String, Integer> vars = new HashMap<>();
    private List<String> issues = new ArrayList<>();
    private Tokenizer tokenizer;
    
    public void run(String code) {
        InputStream in = new ByteArrayInputStream(code.getBytes());
        tokenizer = new Tokenizer(in);
        
        try {
            while (true) {
                TokenBase t = tokenizer.peekToken();
                if (t == null) break;
                
                if (t.type == TokenType.VAR) {
                    processVarAssignment();
                } else {
                    issues.add("Syntax error at line " + tokenizer.currentLine);
                    break;
                }
                
                if (!issues.isEmpty()) break;
            }
        } catch (IOException e) {
            issues.add(e.getMessage());
        }
        
        if (!issues.isEmpty()) {
            System.out.println("error");
        } else {
            for (Map.Entry<String, Integer> entry : vars.entrySet()) {
                System.out.println(entry.getKey() + " = " + entry.getValue());
            }
        }
    }
    
    private void processVarAssignment() throws IOException {
        TokenBase varToken = tokenizer.nextToken();
        if (varToken.type != TokenType.VAR) {
            issues.add("Expected variable at line " + tokenizer.currentLine);
            return;
        }
        String varName = ((VarToken)varToken).name;
        
        TokenBase assignToken = tokenizer.nextToken();
        if (assignToken.type != TokenType.SYM || ((SymbolToken)assignToken).op != '=') {
            issues.add("Expected assignment at line " + tokenizer.currentLine);
            return;
        }
        
        int result;
        try {
            result = parseExpression();
        } catch (RuntimeException e) {
            issues.add(e.getMessage());
            return;
        }
        
        TokenBase endToken = tokenizer.nextToken();
        if (endToken == null || endToken.type != TokenType.PNC || ((PunctToken)endToken).mark != ';') {
            issues.add("Expected semicolon at line " + tokenizer.currentLine);
            return;
        }
        
        vars.put(varName, result);
    }
    
    private int parseExpression() throws IOException {
        int total = parseTerm();
        
        while (true) {
            TokenBase t = tokenizer.peekToken();
            if (t == null || t.type != TokenType.SYM) break;
            
            char op = ((SymbolToken)t).op;
            if (op != '+' && op != '-') break;
            
            tokenizer.nextToken();
            int term = parseTerm();
            
            if (op == '+') {
                total += term;
            } else {
                total -= term;
            }
        }
        
        return total;
    }
    
    private int parseTerm() throws IOException {
        int product = parseFactor();
        
        while (true) {
            TokenBase t = tokenizer.peekToken();
            if (t == null || t.type != TokenType.SYM) break;
            
            char op = ((SymbolToken)t).op;
            if (op != '*') break;
            
            tokenizer.nextToken();
            int factor = parseFactor();
            product *= factor;
        }
        
        return product;
    }
    
    private int parseFactor() throws IOException {
        TokenBase t = tokenizer.nextToken();
        
        switch (t.type) {
            case TokenType.LBR:
                int val = parseExpression();
                t = tokenizer.nextToken();
                if (t == null || t.type != TokenType.RBR) {
                    throw new RuntimeException("Missing closing brace at line " + tokenizer.currentLine);
                }
                return val;
                
            case TokenType.SYM:
                char op = ((SymbolToken)t).op;
                if (op != '+' && op != '-') {
                    throw new RuntimeException("Invalid operator at line " + tokenizer.currentLine);
                }
                int fact = parseFactor();
                return op == '+' ? fact : -fact;
                
            case TokenType.NUM:
                return ((NumberToken)t).val;
                
            case TokenType.VAR:
                String var = ((VarToken)t).name;
                if (!vars.containsKey(var)) {
                    throw new RuntimeException("Unknown variable at line " + tokenizer.currentLine);
                }
                return vars.get(var);
                
            default:
                throw new RuntimeException("Unexpected token at line " + tokenizer.currentLine);
        }
    }
    
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        StringBuilder code = new StringBuilder();
        
        System.out.println("Enter program (blank line to execute):");
        while (true) {
            String line = input.nextLine();
            if (line.isEmpty()) {
                break;
            }
            code.append(line).append("\n");
        }
        
        String program = code.toString();
        ProgramExecutor executor = new ProgramExecutor();
        executor.run(program);
    }
}
