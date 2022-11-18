/*
 * Copyright (C) 2022 RollW
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pers.rollw.calculator.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author RollW
 */
public class OperatorFactory {
    private final Map<String, Operator> sCache = new HashMap<>();

    public OperatorFactory() {
    }

    public Operator create(String symbol) {
        if (symbol == null || !isValidSymbol(symbol)) {
            return null;
        }
        Operator operator = sCache.get(symbol);
        if (operator != null) {
            return operator;
        }
        Operator op = null;
        switch (symbol) {
            case "+":
                op = new PlusOperator();
                break;
            case "-":
                op = new MinusOperator();
                break;
            case "*":
                op = new MultiplyOperator();
                break;
            case "/":
                op = new DivideOperator();
                break;
            case "?":
                op = new SquareRootOperator();
                break;
            case "%":
                op = new RemainderOperator();
                break;
            default:
        }
        if (op != null) {
            sCache.put(op.token(), op);
        }
        return op;
    }

    private static final List<String> sValidSymbols = new ArrayList<>();

    static {
        sValidSymbols.add("+");
        sValidSymbols.add("-");
        sValidSymbols.add("*");
        sValidSymbols.add("/");
        sValidSymbols.add("?");
        sValidSymbols.add("%");
    }

    public static boolean isValidSymbol(String symbol) {
        return sValidSymbols.contains(symbol);
    }

    public static boolean isSelfChangeSymbol(String symbol) {
        return !(!symbol.equals("+") && !symbol.equals("-"));
    }
}
