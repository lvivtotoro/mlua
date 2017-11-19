package org.midnightas.mlua;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.midnightas.mlua.MLuaParser.BlockLambdaContext;
import org.midnightas.mlua.MLuaParser.ClassBodyContext;
import org.midnightas.mlua.MLuaParser.ClassDefStmtContext;
import org.midnightas.mlua.MLuaParser.ClassItemContext;
import org.midnightas.mlua.MLuaParser.ConstructorClassItemContext;
import org.midnightas.mlua.MLuaParser.ExpLambdaContext;
import org.midnightas.mlua.MLuaParser.FuncClassItemContext;
import org.midnightas.mlua.MLuaParser.FuncbodyContext;
import org.midnightas.mlua.MLuaParser.VarClassItemContext;

public class MLua extends MLuaBaseListener {

	private TokenStreamRewriter rewriter;

	private String currentClassName;
	private String currentClassExtends;

	public MLua(CommonTokenStream stream) {
		this.rewriter = new TokenStreamRewriter(stream);
	}

	@Override
	public void enterClassDefStmt(ClassDefStmtContext ctx) {
		rewriter.delete(ctx.start, ctx.contains);

		String className = ctx.name.getText();
		String extendingName = ctx.extending != null ? ctx.extending.getText() : className;

		currentClassName = className;
		currentClassExtends = ctx.extending == null ? null : ctx.extending.getText();

		StringBuilder builder = new StringBuilder();
		builder.append(className).append("={};");
		builder.append(className).append(".__index=").append(className).append(';');
		builder.append("setmetatable(").append(className).append(",{__index=").append(extendingName).append(',');
		builder.append("__call=function(cls,...)local self=setmetatable({},cls)self:init(...)return self;end})");

		rewriter.insertBefore(ctx.start, builder.toString());
	}

	@Override
	public void exitClassDefStmt(ClassDefStmtContext ctx) {
		rewriter.delete(ctx.end);

		currentClassName = null;
	}

	@Override
	public void enterFuncClassItem(FuncClassItemContext ctx) {
		boolean isStatic = ctx.staticKw() != null;
		if (isStatic)
			rewriter.delete(ctx.staticKw().start);

		TerminalNode name = ctx.functiondef().NAME();

		String prefix = currentClassName + (isStatic ? "." : ":");
		rewriter.insertBefore(name.getSymbol(), prefix);
	}

	@Override
	public void enterVarClassItem(VarClassItemContext ctx) {
		// statics only (non-statics handled in below method)
		if (ctx.staticKw() == null)
			return;

		rewriter.delete(ctx.staticKw().start);

		rewriter.insertBefore(ctx.NAME().getSymbol(), currentClassName + ".");
	}

	// !!!!!!!!!!!!!!!
	// ! THIS METHOD ALSO HANDLES NON-STATIC CLASS FIELDS
	// !!!!!!!!!!!!!!!
	@Override
	public void enterConstructorClassItem(ConstructorClassItemContext ctx) {
		FuncbodyContext body = ctx.funcbody();

		rewriter.replace(ctx.constToken, "function " + currentClassName + ":init");

		StringBuilder beforeBlock = new StringBuilder();

		if (currentClassExtends != null) {
			boolean constructorHasParameters = !body.parlist().isEmptyList;

			beforeBlock.append(currentClassExtends).append(".init(self");
			if (constructorHasParameters)
				beforeBlock.append(',');
			beforeBlock.append(body.parlist().getText()).append(')');
		}

		List<ClassItemContext> classItems = ((ClassBodyContext) ctx.getParent()).classItem();
		for (int i = classItems.size() - 1; i >= 0; i--) {
			// we are counting in reverse because we are adding at the *beginning*

			if (!(classItems.get(i) instanceof VarClassItemContext))
				continue;

			VarClassItemContext var = (VarClassItemContext) classItems.get(i);
			if (var.staticKw() == null) {
				// non-static only
				rewriter.delete(var.start, var.stop);

				beforeBlock.append("self." + var.getText());
			}
		}

		rewriter.insertBefore(ctx.funcbody().block().start, beforeBlock.append(' ').toString());
	}

	@Override
	public void enterExpLambda(ExpLambdaContext ctx) {
		// using token indices and -1 to also delete the unnecessary whitespace
		rewriter.replace(ctx.start.getTokenIndex(), ctx.namelist().start.getTokenIndex() - 1, "function(");
		rewriter.replace(ctx.namelist().stop.getTokenIndex() + 1, ctx.namelist().stop.getTokenIndex() + 2, ")return ");
		rewriter.insertAfter(ctx.exp().stop, " end");
	}

	@Override
	public void enterBlockLambda(BlockLambdaContext ctx) {
		// using token indices and -1 to also delete the unnecessary whitespace
		rewriter.replace(ctx.start.getTokenIndex(), ctx.namelist().start.getTokenIndex() - 1, "function(");
		rewriter.replace(ctx.namelist().stop.getTokenIndex() + 1, ctx.namelist().stop.getTokenIndex() + 2, ")");
	}

	@Override
	public String toString() {
		return rewriter.getText();
	}

	public static String compile(String input) {
		CommonTokenStream stream = new CommonTokenStream(new MLuaLexer(CharStreams.fromString(input)));

		MLuaParser parser = new MLuaParser(stream);
		parser.setBuildParseTree(true);

		MLua compiler = new MLua(stream);
		new ParseTreeWalker().walk(compiler, parser.chunk());

		return compiler.toString();
	}

}
