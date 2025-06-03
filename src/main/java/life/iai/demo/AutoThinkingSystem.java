package life.iai.demo;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

// 数据集基类
abstract class DataSet {
    protected String source;
    public DataSet(String source) {
        this.source = source;
    }
    public abstract String getContent();
}

// 知识表示
class Knowledge {
    private static final AtomicLong idCounter = new AtomicLong(0);
    private final long id;
    private final String content;
    private final String source;
    private final long timestamp;

    public Knowledge(String content, String source) {
        this.id = idCounter.getAndIncrement();
        this.content = content;
        this.source = source;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public long getId() { return id; }
    public String getContent() { return content; }
    public String getSource() { return source; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("Knowledge#%d [%tF %<tT]: %s", id, new Date(timestamp), content);
    }
}

// 知识集合
class KnowledgeSet {
    private final Map<Long, Knowledge> knowledgeMap = new HashMap<>();

    public void addKnowledge(Knowledge knowledge) {
        knowledgeMap.put(knowledge.getId(), knowledge);
    }

    public void merge(KnowledgeSet other) {
        knowledgeMap.putAll(other.knowledgeMap);
    }

    public List<Knowledge> getAllKnowledge() {
        return new ArrayList<>(knowledgeMap.values());
    }

    public int size() {
        return knowledgeMap.size();
    }
}

// 工具接口
interface KnowledgeTool {
    String getName();
    KnowledgeSet process(DataSet input);
}

// 工具集管理
class ToolSet {
    private final Map<String, KnowledgeTool> tools = new HashMap<>();

    public void registerTool(KnowledgeTool tool) {
        tools.put(tool.getName(), tool);
    }

    public KnowledgeSet applyTools(DataSet input) {
        KnowledgeSet result = new KnowledgeSet();
        for (KnowledgeTool tool : tools.values()) {
            KnowledgeSet toolResult = tool.process(input);
            result.merge(toolResult);
        }
        return result;
    }

    public void upgradeTool(String name, KnowledgeTool newTool) {
        tools.put(name, newTool);
    }
}

// 记忆系统
class MemorySystem {
    private final KnowledgeSet longTermMemory = new KnowledgeSet();
    private final Map<String, KnowledgeSet> contextualMemory = new HashMap<>();

    public void store(KnowledgeSet knowledge) {
        longTermMemory.merge(knowledge);
    }

    public KnowledgeSet recallContext(String context) {
        return contextualMemory.getOrDefault(context, new KnowledgeSet());
    }

    public void setContext(String context, KnowledgeSet knowledge) {
        contextualMemory.put(context, knowledge);
    }

    public List<Knowledge> searchMemory(String keyword) {
        List<Knowledge> results = new ArrayList<>();
        for (Knowledge k : longTermMemory.getAllKnowledge()) {
            if (k.getContent().contains(keyword)) {
                results.add(k);
            }
        }
        return results;
    }
}

// 思考引擎
class ThinkingEngine {
    private final ToolSet toolSet;
    private final MemorySystem memory;

    public ThinkingEngine(ToolSet toolSet, MemorySystem memory) {
        this.toolSet = toolSet;
        this.memory = memory;
    }

    public KnowledgeSet process(DataSet input) {
        // 步骤1: 使用工具集处理输入数据
        KnowledgeSet newKnowledge = toolSet.applyTools(input);

        // 步骤2: 与记忆中的知识结合
        KnowledgeSet contextKnowledge = memory.recallContext(input.source);
        newKnowledge.merge(contextKnowledge);

        // 步骤3: 存储到记忆系统
        memory.store(newKnowledge);
        memory.setContext(input.source, newKnowledge);

        return newKnowledge;
    }

    public void upgradeTool(String name, KnowledgeTool newTool) {
        toolSet.upgradeTool(name, newTool);
    }
}

// 示例工具实现
class AnalysisTool implements KnowledgeTool {
    @Override
    public String getName() { return "DataAnalyzer"; }

    @Override
    public KnowledgeSet process(DataSet input) {
        KnowledgeSet result = new KnowledgeSet();
        // 模拟数据分析过程
        String content = input.getContent();
        String analysis = "分析结果: " + content.toUpperCase() + " 长度=" + content.length();
        result.addKnowledge(new Knowledge(analysis, "Analyzer"));
        return result;
    }
}

class PatternTool implements KnowledgeTool {
    @Override
    public String getName() { return "PatternFinder"; }

    @Override
    public KnowledgeSet process(DataSet input) {
        KnowledgeSet result = new KnowledgeSet();
        // 模拟模式发现
        String content = input.getContent();
        if (content.contains("Java")) {
            result.addKnowledge(new Knowledge("发现Java相关模式", "PatternFinder"));
        }
        return result;
    }
}

// 示例数据集
class TextDataSet extends DataSet {
    private final String text;

    public TextDataSet(String source, String text) {
        super(source);
        this.text = text;
    }

    @Override
    public String getContent() {
        return text;
    }
}

// 使用示例
public class AutoThinkingSystem {
    public static void main(String[] args) {
        // 初始化系统
        ToolSet toolSet = new ToolSet();
        toolSet.registerTool(new AnalysisTool());
        toolSet.registerTool(new PatternTool());

        MemorySystem memory = new MemorySystem();
        ThinkingEngine engine = new ThinkingEngine(toolSet, memory);

        // 处理第一份数据
        DataSet data1 = new TextDataSet("Source1", "Java是一种编程语言");
        KnowledgeSet result1 = engine.process(data1);
        System.out.println("===== 第一轮处理结果 =====");
        result1.getAllKnowledge().forEach(System.out::println);

        // 处理第二份数据
        DataSet data2 = new TextDataSet("Source2", "Python也是一种编程语言");
        KnowledgeSet result2 = engine.process(data2);
        System.out.println("\n===== 第二轮处理结果 =====");
        result2.getAllKnowledge().forEach(System.out::println);

        // 升级工具
        engine.upgradeTool("PatternFinder", new PatternTool() {
            @Override
            public KnowledgeSet process(DataSet input) {
                KnowledgeSet result = new KnowledgeSet();
                String content = input.getContent();
                if (content.contains("Python")) {
                    result.addKnowledge(new Knowledge("发现Python高级模式", "UpgradedPatternFinder"));
                }
                return result;
            }
        });

        // 使用升级后的工具处理数据
        DataSet data3 = new TextDataSet("Source2", "Python在AI领域很流行");
        KnowledgeSet result3 = engine.process(data3);
        System.out.println("\n===== 工具升级后处理结果 =====");
        result3.getAllKnowledge().forEach(System.out::println);

        // 记忆检索
        System.out.println("\n===== 记忆检索结果 =====");
        memory.searchMemory("Python").forEach(System.out::println);
    }
}