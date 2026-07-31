package com.wgz.aikir.ai.streaming;

import dev.langchain4j.model.chat.response.PartialToolCall;

import java.util.HashMap;
import java.util.Map;

public class ToolPartialExtractor {

    private final Map<Integer, ToolCallBuffer> buffers = new HashMap<>();

    public ExtractResult extract(PartialToolCall partialToolCall) {
        int index = partialToolCall.index();
        String toolName = partialToolCall.name();
        String chunk = partialToolCall.partialArguments();

        ToolCallBuffer buffer = buffers.computeIfAbsent(index, k -> new ToolCallBuffer());
        buffer.append(chunk);
        return buffer.tryExtract(toolName);
    }

    public record ExtractResult(String partialContent, boolean isFirst, boolean hasOutput) {
    }

    private static class ToolCallBuffer {
        private final StringBuilder accumulated = new StringBuilder();
        private final StringBuilder filePathBuilder = new StringBuilder();
        private int jsonPos = 0;
        private boolean filePathComplete = false;
        private boolean headerEmitted = false;
        private String filePath = "";
        private State state = State.SEEK_KEY;
        private boolean inEscape = false;
        private boolean inContentValue = false;
        private boolean inFilePathValue = false;
        private final StringBuilder currentKey = new StringBuilder();

        enum State {
            SEEK_KEY, IN_KEY, AFTER_KEY, SEEK_VALUE, IN_VALUE
        }

        void append(String chunk) {
            accumulated.append(chunk);
        }

        ExtractResult tryExtract(String toolName) {
            StringBuilder newOutput = new StringBuilder();
            boolean firstOutput = false;

            while (jsonPos < accumulated.length()) {
                char c = accumulated.charAt(jsonPos);

                switch (state) {
                    case SEEK_KEY -> {
                        if (c == '"') {
                            currentKey.setLength(0);
                            state = State.IN_KEY;
                        }
                    }
                    case IN_KEY -> {
                        if (c == '"') {
                            state = State.AFTER_KEY;
                        } else {
                            currentKey.append(c);
                        }
                    }
                    case AFTER_KEY -> {
                        if (c == ':') {
                            state = State.SEEK_VALUE;
                        }
                    }
                    case SEEK_VALUE -> {
                        if (c == '"') {
                            String key = currentKey.toString();
                            inContentValue = "content".equals(key);
                            inFilePathValue = "relativeFilePath".equals(key);
                            if (inFilePathValue) {
                                filePathBuilder.setLength(0);
                            }
                            state = State.IN_VALUE;
                            inEscape = false;
                        } else if (c == '{' || c == '[' || c == 't' || c == 'f' || c == 'n' || c == '-' || Character.isDigit(c)) {
                            state = State.IN_VALUE;
                            inContentValue = false;
                            inFilePathValue = false;
                            inEscape = false;
                        }
                    }
                    case IN_VALUE -> {
                        if (inContentValue || inFilePathValue) {
                            if (inEscape) {
                                char unescaped = unescape(c);
                                if (inContentValue) {
                                    newOutput.append(unescaped);
                                }
                                if (inFilePathValue) {
                                    filePathBuilder.append(unescaped);
                                }
                                inEscape = false;
                            } else if (c == '\\') {
                                inEscape = true;
                            } else if (c == '"') {
                                if (inFilePathValue && !filePathComplete) {
                                    filePath = filePathBuilder.toString();
                                    filePathComplete = true;
                                }
                                inContentValue = false;
                                inFilePathValue = false;
                                state = State.SEEK_KEY;
                            } else {
                                if (inContentValue) {
                                    newOutput.append(c);
                                }
                                if (inFilePathValue) {
                                    filePathBuilder.append(c);
                                }
                            }
                        } else {
                            if (inEscape) {
                                inEscape = false;
                            } else if (c == '\\') {
                                inEscape = true;
                            } else if (c == '"') {
                                state = State.SEEK_KEY;
                            }
                        }
                    }
                }
                jsonPos++;
            }

            if (newOutput.length() > 0) {
                if (!headerEmitted && filePathComplete) {
                    String suffix = getSuffix(filePath);
                    String header = String.format("[工具调用] 写入文件 %s\n```%s\n", filePath, suffix);
                    newOutput.insert(0, header);
                    headerEmitted = true;
                    firstOutput = true;
                }
                return new ExtractResult(newOutput.toString(), firstOutput, true);
            }
            return new ExtractResult("", false, false);
        }

        private char unescape(char c) {
            return switch (c) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                default -> c;
            };
        }

        private String getSuffix(String filePath) {
            int dotIdx = filePath.lastIndexOf('.');
            if (dotIdx >= 0 && dotIdx < filePath.length() - 1) {
                return filePath.substring(dotIdx + 1);
            }
            return "";
        }
    }
}
// @zbiti-ai:f:167:32c909f5
