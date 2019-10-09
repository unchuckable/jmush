package com.github.unchuckable.jmush.mushcode;

public interface Expression {
	
	Value evaluateExpression( ExecutionContext context );
	boolean isConstant();
	
}
