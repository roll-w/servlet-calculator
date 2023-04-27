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

package tech.rollw.calculator.core;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.*;

/**
 * @author RollW
 */
public class CalculatorEvaluator {
    private final String expression;
    private final OperatorFactory factory;

    public CalculatorEvaluator(String expression) {
        this.expression = expression;
        factory = new OperatorFactory();
    }

    private List<Block> segments() {
        List<Segment> segments = new ArrayList<>();
        CharacterIterator iterator = new StringCharacterIterator(trim(expression));

        char next = iterator.current();
        int index = 0;

        StringBuilder numBuilder = new StringBuilder();
        StringBuilder operatorBuilder = new StringBuilder();
        if (isNumber(next)) {
            numBuilder.append(next);
        } else {
            operatorBuilder.append(next);
        }

        while ((next = iterator.next()) != CharacterIterator.DONE) {
            if (isNumber(next)) {
                numBuilder.append(next);
                if (operatorBuilder.length() != 0) {
                    List<Segment> symbols =
                            symbolSegments(operatorBuilder.toString(), index);
                    index += symbols.size();
                    segments.addAll(symbols);
                    operatorBuilder = new StringBuilder();
                }
                continue;
            }

            operatorBuilder.append(next);
            String currentNum = numBuilder.toString();
            Segment segment;
            if (currentNum.isEmpty()) {
                segment = null;
            } else {
                segment = new Segment(
                        SegmentType.NUM,
                        currentNum,
                        parseDouble(currentNum),
                        index
                );
            }

            numBuilder = new StringBuilder();
            if (segment != null) {
                segments.add(segment);
                index++;
            }
        }
        if (numBuilder.length() != 0) {
            segments.add(new Segment(
                    SegmentType.NUM,
                    numBuilder.toString(),
                    parseDouble(numBuilder.toString()),
                    index++)
            );
        }
        if (operatorBuilder.length() != 0) {
            segments.add(new Segment(SegmentType.OPERATOR, operatorBuilder.toString(), index));
        }

        segments.sort(Comparator.comparingInt(s -> s.index));

        return Collections.unmodifiableList(segments);
    }

    private List<Segment> symbolSegments(String symbols, int index) {
        if (OperatorFactory.isValidSymbol(symbols)) {
            return Collections.singletonList(
                    new Segment(SegmentType.OPERATOR, symbols, index));
        }
        CharacterIterator iterator = new StringCharacterIterator(symbols);
        List<Segment> segments = new ArrayList<>();
        char next = iterator.current();
        int tmpIdx = index;
        StringBuilder symbolsBuilder = new StringBuilder();
        do {
            symbolsBuilder.append(next);
            if (OperatorFactory.isValidSymbol(symbolsBuilder.toString())) {
                Segment segment = new Segment(
                        SegmentType.OPERATOR,
                        String.valueOf(next), tmpIdx
                );
                tmpIdx++;
                segments.add(segment);
                symbolsBuilder = new StringBuilder();
            }
        } while (((next = iterator.next()) != CharacterIterator.DONE));
        if (symbolsBuilder.length() != 0) {
            segments.add(
                    new Segment(SegmentType.OPERATOR, symbolsBuilder.toString(), tmpIdx));
        }
        return segments;
    }


    public Result evaluate() throws CalculatorException {
        Result result = evaluateWithReduce();
        if (Double.isNaN(result.result)) {
            throw new CalculatorException(
                    CalculatorException.ErrorInfo.ILLEGAL_ARITHMETIC,
                    "Maybe you want to divide by 0 or try to root a negative number.");
        }
        return result;
    }

    private Result evaluateWithReduce() {
        List<Block> segments = segments();
        List<ParseStage> parseStages = new ArrayList<>();
        parseStages.add(new ParseStage("INIT", segments));
        Stage stage = new Stage(segments, Collections.emptyList());
        for (Level level : Level.iterator()) {
            stage = reduce(stage, level);
            parseStages.add(new ParseStage(level.toString(), stage.blocks));
        }
        if (stage.blocks.isEmpty()) {
            throw new CalculatorException(CalculatorException.ErrorInfo.UNKNOWN,
                    "Unknown error. [Result-Empty]");
        }
        if (stage.blocks.size() > 1) {
            throw new CalculatorException(CalculatorException.ErrorInfo.NON_COMPLIANT_OPERATORS,
                    "You input a prefix operator but not has a suffix operator.");
        }
        return new Result(stage.steps, stage.blocks.get(0).num, parseStages);
    }

    private Stage reduce(Stage stage, Level computeLevel) {
        BlockStack blockStack = new BlockStack();
        BlockContainer container = new BlockContainer(stage.blocks);
        List<Step> steps = new ArrayList<>(stage.steps);

        for (int i = 0; i <= container.lastIndex(); i++) {
            Block block = container.findBlock(i);
            if (block.type == SegmentType.NUM) {
                blockStack.push(block);
                i = block.end;
                continue;
            }

            Operator operator = factory.create(block.expression);
            if (operator == null) {
                throw new CalculatorException(CalculatorException.ErrorInfo.UNKNOWN_OPERATOR,
                        String.format("Unknown operator '%s' in [%d-%d]", block.expression,
                                block.start, block.end + block.expression.length()));
            }

            if (operator.priority() == computeLevel) {
                Block next = mergeBlocks(container.blocksToNextNum(i + 1));
                Block prev;
                if (operator.type() == Type.PREFIX) {
                    prev = null;
                } else {
                    prev = blockStack.pop();
                }

                double prNum = prev == null ? Operator.INVALID : prev.num;
                double r = operator.operate(
                        prNum,
                        next.num
                );
                Step step = new Step(prNum, next.num, r, operator.token());
                steps.add(step);
                Block newBlock = new Block(
                        SegmentType.NUM,
                        String.valueOf(r),
                        r,
                        prev == null ? block.start : prev.start,
                        next.end
                );

                i = next.end;
                blockStack.push(newBlock);
                continue;
            }

            blockStack.push(block);
        }
        return new Stage(new ArrayList<>(blockStack), steps);
    }

    private Block mergeBlocks(List<Block> blocks) {
        List<Block> reversed = new ArrayList<>(blocks);
        StringBuilder builder = new StringBuilder();
        blocks.forEach(b -> builder.append(b.expression));

        Collections.reverse(reversed);
        Block first = reversed.stream().findFirst().orElse(null);
        Block last = blocks.stream().findFirst().orElse(null);

        if (first == null || last == null) {
            throw new CalculatorException(CalculatorException.ErrorInfo.REDUCED_ALL);
        }
        double init = first.num;
        for (int i = 1; i < reversed.size(); i++) {
            Block block = reversed.get(i);
            if (block.type != SegmentType.OPERATOR) {
                break;
            }
            if (!OperatorFactory.isSelfChangeSymbol(block.expression)) {
                throw new CalculatorException(CalculatorException.ErrorInfo.ILLEGAL_OPERATOR,
                        String.format("Illegal duplicated operator '%s' in [%d-%d].", block.expression, block.start, block.end));
            }
            Operator operator = factory.create(block.expression);
            if (operator == null) {
                throw new CalculatorException(CalculatorException.ErrorInfo.UNKNOWN_OPERATOR,
                        String.format("Unknown operator '%s' in [%d-%d]", block.expression, block.start, block.end));
            }
            init = operator.operate(Operator.INVALID, init);
        }
        return new Block(SegmentType.NUM, builder.toString(), init, last.start, first.end);
    }

    private static boolean isNumber(char c) {
        if (Character.isDigit(c)) {
            return true;
        }
        return c == '.';
    }

    private static String trim(String s) {
        return s.replaceAll("\\s*", "");
    }

    public static class Result {
        public final List<Step> steps;
        public final double result;
        public final List<ParseStage> stages;

        private Result(List<Step> steps,
                       double result,
                       List<ParseStage> stages) {
            this.steps = steps;
            this.result = result;
            this.stages = stages;
        }
    }

    public static class ParseStage {
        public final String stage;
        public final List<Block> blocks;

        private ParseStage(String stage, List<Block> blocks) {
            this.stage = stage;
            this.blocks = blocks;
        }
    }

    public static class Step {
        public final double prev;
        public final double next;
        public final double res;
        public final String operator;

        private Step(double prev, double next, double res, String operator) {
            this.prev = prev;
            this.next = next;
            this.res = res;
            this.operator = operator;
        }

        @Override
        public String toString() {
            return String.format("step;type=%s;pr=%.3f;ne=%.3f;r=%.3f", operator, prev, next, res);
        }
    }

    private static class Segment extends Block {
        public final int index;

        private Segment(SegmentType type, String expression, int index) {
            super(type, expression, index, index);
            this.index = index;
        }

        private Segment(SegmentType type, String expression, double num, int index) {
            super(type, expression, num, index, index);
            this.index = index;
        }

        @Override
        public String toString() {
            return "Segment{" +
                    "index=" + index +
                    ", type=" + type +
                    ", expression='" + expression + '\'' +
                    ", num=" + num +
                    '}';
        }
    }

    public static class Block {
        public final SegmentType type;
        public final String expression;
        public final double num;
        public final int start;
        public final int end;

        private Block(SegmentType type, String expression, double num, int start, int end) {
            this.type = type;
            this.expression = expression;
            this.num = num;
            this.start = start;
            this.end = end;
        }

        private Block(SegmentType type, String expression, int start, int end) {
            this.type = type;
            this.expression = expression;
            this.num = Operator.INVALID;
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return "Block{" +
                    "type=" + type +
                    ", expression='" + expression + '\'' +
                    ", num=" + num +
                    ", start=" + start +
                    ", end=" + end +
                    '}';
        }
    }

    private static class Stage {
        public final List<Block> blocks;
        public final List<Step> steps;

        private Stage(List<Block> blocks, List<Step> steps) {
            this.blocks = blocks;
            this.steps = steps;
        }
    }

    private enum SegmentType {
        NUM, OPERATOR
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new CalculatorException(CalculatorException.ErrorInfo.NON_COMPLIANT_NUMBERS,
                    "Illegal number: " + s);
        }
    }

    @SuppressWarnings("all")
    private static class BlockContainer {
        private final List<Block> blocks;

        private BlockContainer(List<Block> blocks) {
            this.blocks = blocks;
        }

        public int lastIndex() {
            return blocks.get(blocks.size() - 1).end;
        }

        public Block findBlock(int start) {
            for (Block block : blocks) {
                if (block.start == start || block.end == start) {
                    return block;
                }
                if (start > block.start && start < block.end) {
                    return block;
                }
            }
            throw new CalculatorException(CalculatorException.ErrorInfo.NON_COMPLIANT_OPERATORS,
                    "Expression incomplete");
        }

        public Block findNextNumBlock(int start) {
            Block block = findBlock(start);
            if (block == null) {
                return null;
            }
            if (block.type == SegmentType.NUM) {
                return block;
            }
            return findNextNumBlock(start + 1);
        }

        public List<Block> blocksToNextNum(int start) {
            Block block = findNextNumBlock(start);
            if (block == null) {
                throw new IllegalArgumentException();
            }
            Block with = findBlock(start);
            if (block.end - with.end == 0) {
                return Collections.singletonList(block);
            }
            List<Block> bls = new ArrayList<>();
            StringBuilder builder = new StringBuilder();
            double res = 0;
            for (int i = with.start; i <= block.end; i++) {
                Block b = findBlock(i);
                i = b.end;
                bls.add(b);
            }
            return bls;
        }

        public Block mergeBlock(int start) {
            Block block = findNextNumBlock(start);
            if (block == null) {
                throw new IllegalArgumentException();
            }
            Block with = findBlock(start);
            if (block.end - with.end == 0) {
                return block;
            }

            StringBuilder builder = new StringBuilder();
            double res = 0;
            for (int i = with.start; i <= block.end; i++) {
                Block b = findBlock(i);
                i = b.end;
                builder.append(b.expression);
                res += b.num;
            }
            return new Block(SegmentType.NUM,
                    builder.toString(),
                    res,
                    with.start,
                    block.end
            );
        }
    }

    private static class BlockStack extends Stack<Block> {
        public BlockStack() {
            super();
        }

        public boolean containsIndex(int index) {
            for (Object element : elementData) {
                Block block = (Block) element;
                if (block == null) {
                    return false;
                }
                if (index == block.end || index == block.start) {
                    return true;
                }
                if (index > block.start && index < block.end) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Block push(Block item) {
            if (containsIndex(item.start) || containsIndex(item.end)) {
                return item;
            }
            return super.push(item);
        }

        @Override
        public synchronized Block pop() {
            try {
                return super.pop();
            } catch (Exception e) {
                return null;
            }
        }
    }
}
