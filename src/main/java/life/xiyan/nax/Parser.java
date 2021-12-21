package life.xiyan.nax;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static life.xiyan.nax.TokenType.*;

public class Parser {

    private final List<Token> tokens;
    private int current = 0;
    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }
    // each grammar rule becomes a method inside this new class

    List<Stmt> parse() {
        // a program is a list of statements
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) statements.add(declaration());

        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) initializer = expression();

        consume(SEMICOLON, "Expect ';' after variable declaration");
        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        return expressionStatement();
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;

        // eagerly looks for an else
        if (match(ELSE)) elseBranch = statement();

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(SEMICOLON)) initializer = null;
        else if (match(VAR)) initializer = varDeclaration();
        else initializer = expressionStatement();

        Expr condition = null;
        if (!check(SEMICOLON)) condition = expression();
        consume(SEMICOLON, "Expect ';' after loop condition");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) increment = expression();

        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();

        // desugar to while
        if (increment != null) body = new Stmt.Block(Arrays.asList(
                body,
                new Stmt.Expression(increment)
        ));

        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        if (initializer != null) body = new Stmt.Block(Arrays.asList(initializer, body));

        return body;
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block");
        return statements;
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression");
        return new Stmt.Expression(expr);
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        // right before we create the assignment expression node, we look at the left-hand side
        // expression and figure out what kind of assigment target it is; we convert the r-value
        // expression node into an l-value representation.
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            // check if the left-hand side is a valid assignment target
            if (expr instanceof Expr.Variable) {
                Token name = (((Expr.Variable) expr).name);
                return new Expr.Assign(name, value);
            }

            // we don't throw because the parser isn't in a confused state where we need to go into
            // panic mode and synchronize
            //noinspection ThrowableNotThrown
            error(equals, "Invalid assignment target");
        }
        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    // equality       → comparison ( ( "!=" | "==" ) comparison )* ;
    private Expr equality() {
        // first comparison non-terminal
        Expr expr = comparison();

        // loop because we allow a == b == c !== d ...
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            // call comparison() again to parse the right-hand operand
            Expr right = comparison();
            // combine the operator and its two operands into a new Expr.Binary syntax tree node
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
    private Expr comparison() {
        // virtually identical to equality()
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // unary          → ( "!" | "-" ) unary | primary ;
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    //    primary        → NUMBER | STRING | "true" | "false" | "nil"| "(" expression ")" ;
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(true);
        if (match(NUMBER, STRING)) return new Expr.Literal(previous().literal);
        if (match(IDENTIFIER)) return new Expr.Variable(previous());
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            // after we match an opening ( and parse the expression inside it, we must find a )
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        // as the parser descends through the parsing methods for each grammar rule, it eventually
        // hit a primary. If one of the cases in there match, it means we are sitting on a token
        // that can't start an expression.
        throw error(peek(), "Expect expression.");
    }

    // check to see if the current token has any of the give types
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    // returns true if the current token is of the given type
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    // consumes the current token and returns it
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    // check if we've run out of tokens to parse
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    // entering panic mode
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    // panic mode recovery

    private ParseError error(Token token, String message) {
        Nax.error(token, message);
        return new ParseError();
    }

    // we want ot discard tokens until we're right at the beginning of the next statement
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }
            advance();
        }
    }

    // synchronize the recursive descent parser

    private static class ParseError extends RuntimeException {
    }

}
