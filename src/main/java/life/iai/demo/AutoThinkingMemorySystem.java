package life.iai.demo;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.util.Map.entry;

// 数据集基类
abstract class DataSet {
    protected String source;

    public DataSet(String source) {
        this.source = source;
    }

    public abstract String getContent();
}

// 知识表示（添加关联关系）
class Knowledge {
    private static final AtomicLong idCounter = new AtomicLong(0);
    private long id;
    private final String content;
    private final String source;
    private String summary;
    private final long timestamp;
    private final Set<Long> relatedKnowledgeIds = new HashSet<>();

    public Knowledge(String content, String source) {
        this.id = idCounter.getAndIncrement();
        this.content = content;
        this.source = source;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        // 注意：实际应用中需要更安全的ID管理
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public String getSource() {
        return source;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Set<Long> getRelatedKnowledgeIds() {
        return new HashSet<>(relatedKnowledgeIds);
    }

    // 添加关联知识
    public void addRelatedKnowledge(long knowledgeId) {
        if (knowledgeId != this.id) {
            relatedKnowledgeIds.add(knowledgeId);
        }
    }

    // 添加多个关联知识
    public void addAllRelatedKnowledge(Collection<Long> knowledgeIds) {
        knowledgeIds.stream()
                .filter(id -> id != this.id)
                .forEach(relatedKnowledgeIds::add);
    }

    @Override
    public String toString() {
        return String.format("Knowledge#%d [%tF %<tT]: %s", id, new Date(timestamp), content);
    }

    // 获取关联知识详情
    public String toDetailedString(MemorySystem memory) {
        String relations = relatedKnowledgeIds.isEmpty() ? "无关联" :
                relatedKnowledgeIds.stream()
                        .map(id -> "#" + id)
                        .collect(Collectors.joining(", "));

        return String.format("Knowledge#%d [%tF %<tT]\n内容: %s\n来源: %s\n关联: %s",
                id, new Date(timestamp), content, source, relations);
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}

// 知识集合
class KnowledgeSet {
    private final Map<Long, Knowledge> knowledgeMap = new HashMap<>();

    public void addKnowledge(Knowledge knowledge) {
        knowledgeMap.put(knowledge.getId(), knowledge);
    }

    public void merge(KnowledgeSet other) {
        other.knowledgeMap.values().forEach(this::addKnowledge);
    }

    public List<Knowledge> getAllKnowledge() {
        return new ArrayList<>(knowledgeMap.values());
    }

    public Knowledge getKnowledge(long id) {
        return knowledgeMap.get(id);
    }

    public int size() {
        return knowledgeMap.size();
    }
}

// 工具接口
interface KnowledgeTool {
    String getName();

    KnowledgeSet process(DataSet input, KnowledgeSet context);
}

// 工具链编排系统
class ToolChain {
    private final List<KnowledgeTool> toolSequence = new ArrayList<>();
    private final Map<String, KnowledgeTool> toolMap = new HashMap<>();

    public void addTool(KnowledgeTool tool, int position) {
        if (position < 0 || position > toolSequence.size()) {
            throw new IllegalArgumentException("无效的位置: " + position);
        }
        toolSequence.add(position, tool);
        toolMap.put(tool.getName(), tool);
    }

    public void addTool(KnowledgeTool tool) {
        addTool(tool, toolSequence.size());
    }

    public KnowledgeSet execute(DataSet input, KnowledgeSet historicalContext) {
        KnowledgeSet currentResult = new KnowledgeSet(); // 本次处理中逐步积累的知识
        for (KnowledgeTool tool : toolSequence) {
            // 创建当前步骤的上下文：历史上下文 + 本次已生成的知识
            KnowledgeSet stepContext = new KnowledgeSet();
            stepContext.merge(historicalContext); // 加入历史上下文
            stepContext.merge(currentResult);    // 加入本次处理中前面工具生成的知识

            // 执行当前工具
            KnowledgeSet toolResult = tool.process(input, stepContext);

            // 将当前工具生成的知识合并到当前结果
            currentResult.merge(toolResult);
        }
        return currentResult;
    }

    public void upgradeTool(String name, KnowledgeTool newTool) {
        if (!toolMap.containsKey(name)) {
            throw new IllegalArgumentException("未找到工具: " + name);
        }

        // 更新工具链中的工具
        for (int i = 0; i < toolSequence.size(); i++) {
            if (toolSequence.get(i).getName().equals(name)) {
                toolSequence.set(i, newTool);
                toolMap.put(name, newTool);
                return;
            }
        }
    }

    public List<KnowledgeTool> getToolSequence() {
        return new ArrayList<>(toolSequence);
    }

    public List<String> getToolSequenceName() {
        return toolSequence.stream()
                .map(KnowledgeTool::getName)
                .collect(Collectors.toList());
    }

    public KnowledgeTool getToolByName(String name) {
        return toolMap.get(name);
    }
}

// 记忆系统（添加知识关联功能）
class MemorySystem {
    private final KnowledgeSet longTermMemory = new KnowledgeSet();
    private final Map<String, KnowledgeSet> contextualMemory = new HashMap<>();
    private final Map<String, Set<Long>> keywordIndex = new HashMap<>();
    // 添加公司索引
    private final Map<String, Set<Long>> companyIndex = new HashMap<>();

    public void store(KnowledgeSet knowledge) {
        knowledge.getAllKnowledge().forEach(k -> {
            // 检查知识是否已存在
            Knowledge existing = longTermMemory.getKnowledge(k.getId());
            if (existing != null) {
                // 更新现有知识而不是添加新实例
                if (existing instanceof InvestmentKnowledge && k instanceof InvestmentKnowledge) {
                    InvestmentKnowledge existingIK = (InvestmentKnowledge) existing;
                    InvestmentKnowledge newIK = (InvestmentKnowledge) k;
                    existingIK.updateConfidence(newIK.getConfidence());
                }
            } else {
                longTermMemory.addKnowledge(k);
            }
            indexKnowledge(k);
        });
    }


    public KnowledgeSet recallContext(String context) {
        return contextualMemory.getOrDefault(context, new KnowledgeSet());
    }

    public void setContext(String context, KnowledgeSet knowledge) {
        contextualMemory.put(context, knowledge);
    }

    public List<Knowledge> searchMemory(String keyword) {
        Set<Long> ids = keywordIndex.getOrDefault(keyword.toLowerCase(), Collections.emptySet());
        return ids.stream()
                .map(longTermMemory::getKnowledge)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // 知识关联方法
    public void relateKnowledges(long id1, long id2) {
        Knowledge k1 = longTermMemory.getKnowledge(id1);
        Knowledge k2 = longTermMemory.getKnowledge(id2);

        if (k1 != null && k2 != null) {
            k1.addRelatedKnowledge(k2.getId());
            k2.addRelatedKnowledge(k1.getId());
        }
    }

    // 自动关联相关主题的知识
    public void autoRelateByTopic(String topic) {
        List<Knowledge> related = semanticSearchMemory(topic);
        if (related.size() < 2) return;

        for (int i = 0; i < related.size(); i++) {
            Knowledge current = related.get(i);
            for (int j = i + 1; j < related.size(); j++) {
                current.addRelatedKnowledge(related.get(j).getId());
                related.get(j).addRelatedKnowledge(current.getId());
            }
        }
    }

    // 获取知识详情（带关联关系）
    public String getKnowledgeDetails(long id) {
        Knowledge k = longTermMemory.getKnowledge(id);
        return (k != null) ? k.toDetailedString(this) : "知识不存在";
    }

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "the", "and", "of", "to", "a", "in", "is", "it", "that", "this",
            "的", "是", "在", "和", "了", "有", "就", "也", "与", "等", "为", "我", "你", "他", "我们", "他们"
    ));

    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            entry("ai", "人工智能"),
            entry("人工智能", "ai"),
            entry("腾讯", "tencent"),
            entry("tencent", "腾讯"),
            entry("阿里", "阿里巴巴"),
            entry("阿里巴巴", "阿里"),
            entry("投资", "invest"),
            entry("invest", "投资")
    );

    private void indexKnowledge(Knowledge knowledge) {
        String content = knowledge.getContent().toLowerCase();

        // 使用改进的分词算法（支持中文）
        List<String> tokens = improvedTokenizer(content);
        Map<String, Integer> termFrequency = new HashMap<>();

        // 计算词频并过滤
        for (String token : tokens) {
            // 同义词归一化
            String normalized = SYNONYMS.getOrDefault(token, token);

            if (!STOP_WORDS.contains(normalized) && normalized.length() > 1) {
                termFrequency.put(normalized, termFrequency.getOrDefault(normalized, 0) + 1);
            }
        }

        // 按词频排序（取前5个关键词）
        List<Map.Entry<String, Integer>> sortedTerms = termFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        // 创建知识摘要（含位置权重）
        String summary = createSummary(content, sortedTerms);
        knowledge.setSummary(summary); // 假设Knowledge类添加了setSummary方法

        // 建立索引
        for (Map.Entry<String, Integer> entry : sortedTerms) {
            String term = entry.getKey();
            keywordIndex.computeIfAbsent(term, k -> new HashSet<>())
                    .add(knowledge.getId());
        }

        // 添加公司索引
        if (knowledge instanceof InvestmentKnowledge) {
            InvestmentKnowledge ik = (InvestmentKnowledge) knowledge;
            if (ik.getType() == InvestmentKnowledge.KnowledgeType.COMPANY_ANALYSIS) {
                String company = ik.getCompanyName();
                if (company != null && !company.isEmpty()) {
                    companyIndex.computeIfAbsent(company, k -> new HashSet<>())
                            .add(ik.getId());
                }
            }
        }
    }

    // 添加公司查询方法
    public List<Knowledge> searchByCompany(String company) {
        Set<Long> ids = companyIndex.getOrDefault(company, Collections.emptySet());
        return ids.stream()
                .map(this::getKnowledgeById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // 改进的分词器（支持中英文混合）
    private List<String> improvedTokenizer(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();

        // 增强中文分词逻辑
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // 处理中文字符
            if (isCJKCharacter(c)) {
                // 中文连续字符处理
                currentWord.append(c);

                // 检查下一个字符是否是中文
                boolean nextIsCJK = (i + 1 < text.length()) && isCJKCharacter(text.charAt(i + 1));

                // 如果不是中文或到达结尾，添加当前词
                if (!nextIsCJK || i == text.length() - 1) {
                    tokens.add(currentWord.toString());
                    currentWord.setLength(0);
                }
            }
            // 处理英文字符
            else if (Character.isLetterOrDigit(c)) {
                currentWord.append(c);
            }
            // 分隔符处理
            else if (!currentWord.isEmpty()) {
                tokens.add(currentWord.toString());
                currentWord.setLength(0);
            }
        }

        if (!currentWord.isEmpty()) {
            tokens.add(currentWord.toString());
        }

        return tokens;
    }

    // 判断中日韩文字字符
    private boolean isCJKCharacter(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
    }

    // 创建知识摘要（突出关键词）
    private String createSummary(String content, List<Map.Entry<String, Integer>> keywords) {
        String summary = content;

        // 截取前100字符作为基础摘要
        if (summary.length() > 100) {
            summary = summary.substring(0, 100) + "...";
        }

        // 高亮显示关键词
        for (Map.Entry<String, Integer> kw : keywords) {
            String term = kw.getKey();
            summary = summary.replace(term, "[" + term + "]");
        }

        return summary;
    }

    public List<Knowledge> semanticSearchMemory(String query) {
        // 1. 查询解析
        List<String> queryTerms = improvedTokenizer(query.toLowerCase());
        Map<String, Integer> termWeights = new HashMap<>();


        // 2. 计算查询词权重
        for (String term : queryTerms) {
            String normalized = SYNONYMS.getOrDefault(term, term);
            if (!STOP_WORDS.contains(normalized)) {
                termWeights.put(normalized, termWeights.getOrDefault(normalized, 0) + 1);
            }
        }

        // 3. 知识相关性评分
        Map<Long, Integer> knowledgeScores = new HashMap<>();
        for (Map.Entry<String, Integer> entry : termWeights.entrySet()) {
            String term = entry.getKey();
            int weight = entry.getValue();

            Set<Long> knowledgeIds = keywordIndex.getOrDefault(term, Collections.emptySet());
            for (Long id : knowledgeIds) {
                knowledgeScores.put(id, knowledgeScores.getOrDefault(id, 0) + weight);
            }
        }

        // 4. 排序并返回结果
        return knowledgeScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .map(entry -> longTermMemory.getKnowledge(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // 添加按ID获取知识的方法
    public Knowledge getKnowledgeById(long id) {
        return longTermMemory.getKnowledge(id);
    }
}

// 思考引擎（使用工具链）
class ThinkingEngine {
    private final ToolChain toolChain;
    protected final MemorySystem memory;

    public ThinkingEngine(ToolChain toolChain, MemorySystem memory) {
        this.toolChain = toolChain;
        this.memory = memory;
    }

    public KnowledgeSet process(DataSet input) {
        // 获取相关上下文知识
        KnowledgeSet contextKnowledge = memory.recallContext(input.source);

        // 使用工具链处理输入数据
        KnowledgeSet newKnowledge = toolChain.execute(input, contextKnowledge);

        // 与记忆中的知识结合
        newKnowledge.merge(contextKnowledge);

        // 存储到记忆系统
        memory.store(newKnowledge);

        // 创建新的上下文：合并历史上下文和新知识
        KnowledgeSet mergedContext = new KnowledgeSet();
        mergedContext.merge(contextKnowledge);
        mergedContext.merge(newKnowledge);
        memory.setContext(input.source, mergedContext);

        // 自动关联相关主题的知识
//        memory.autoRelateByTopic("Java");
//        memory.autoRelateByTopic("Python");
//        memory.autoRelateByTopic("AI");

        return newKnowledge;
    }

    public void upgradeTool(String name, KnowledgeTool newTool) {
        toolChain.upgradeTool(name, newTool);
    }

    public List<String> getToolSequence() {
        return toolChain.getToolSequenceName();
    }
}

// 示例工具实现
class AnalysisTool implements KnowledgeTool {
    @Override
    public String getName() {
        return "DataAnalyzer";
    }

    @Override
    public KnowledgeSet process(DataSet input, KnowledgeSet context) {
        KnowledgeSet result = new KnowledgeSet();
        String content = input.getContent();

        // 基础分析
        String analysis = "分析结果: " + content.toUpperCase() + " 长度=" + content.length();
        result.addKnowledge(new Knowledge(analysis, "Analyzer"));

        // 使用上下文增强分析
        if (!context.getAllKnowledge().isEmpty()) {
            String contextEnhanced = analysis + " | 上下文知识数: " + context.size();
            result.addKnowledge(new Knowledge(contextEnhanced, "Analyzer+"));
        }

        return result;
    }
}

class PatternTool implements KnowledgeTool {
    @Override
    public String getName() {
        return "PatternFinder";
    }

    @Override
    public KnowledgeSet process(DataSet input, KnowledgeSet context) {
        KnowledgeSet result = new KnowledgeSet();
        String content = input.getContent();

        // 模式发现
        if (content.contains("Java")) {
            result.addKnowledge(new Knowledge("发现Java相关模式", "PatternFinder"));
        }
        if (content.contains("Python")) {
            result.addKnowledge(new Knowledge("发现Python相关模式", "PatternFinder"));
        }
        if (content.contains("AI")) {
            result.addKnowledge(new Knowledge("发现AI相关模式", "PatternFinder"));
        }

        return result;
    }
}

class RelationTool implements KnowledgeTool {
    @Override
    public String getName() {
        return "RelationBuilder";
    }

    @Override
    public KnowledgeSet process(DataSet input, KnowledgeSet context) {
        KnowledgeSet result = new KnowledgeSet();
        String content = input.getContent();

        // 构建关系知识
        if (content.contains("Java") && content.contains("Python")) {
            result.addKnowledge(new Knowledge("Java与Python有协同关系", "RelationBuilder"));
        }
        if (content.contains("Python") && content.contains("AI")) {
            result.addKnowledge(new Knowledge("Python与AI有强关联", "RelationBuilder"));
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
public class AutoThinkingMemorySystem {
    public static void main(String[] args) {
        // 初始化工具链
        ToolChain toolChain = new ToolChain();
        toolChain.addTool(new AnalysisTool());      // 位置0
        toolChain.addTool(new PatternTool());      // 位置1
        toolChain.addTool(new RelationTool());     // 位置2

        MemorySystem memory = new MemorySystem();
        ThinkingEngine engine = new ThinkingEngine(toolChain, memory);

        System.out.println("工具链顺序: " + engine.getToolSequence());

        // 处理第一份数据
        System.out.println("\n===== 处理第一份数据 =====");
        DataSet data1 = new TextDataSet("Source1", "Java是一种面向对象的编程语言");
        KnowledgeSet result1 = engine.process(data1);
        result1.getAllKnowledge().forEach(System.out::println);

        // 处理第二份数据
        System.out.println("\n===== 处理第二份数据 =====");
        DataSet data2 = new TextDataSet("Source2", "Python在AI领域广泛使用");
        KnowledgeSet result2 = engine.process(data2);
        result2.getAllKnowledge().forEach(System.out::println);

        // 处理第三份数据
        System.out.println("\n===== 处理第三份数据 =====");
        DataSet data3 = new TextDataSet("Source3", "Java和Python都是优秀的编程语言");
        KnowledgeSet result3 = engine.process(data3);
        result3.getAllKnowledge().forEach(System.out::println);

        // 手动添加关联
        memory.relateKnowledges(0, 2);
        memory.relateKnowledges(1, 5);

        // 升级PatternFinder工具
        engine.upgradeTool("PatternFinder", new PatternTool() {
            @Override
            public KnowledgeSet process(DataSet input, KnowledgeSet context) {
                KnowledgeSet result = new KnowledgeSet();
                String content = input.getContent();

                // 增强版模式发现
                if (content.contains("Java")) {
                    result.addKnowledge(new Knowledge("发现Java高级特性模式", "PatternFinderPro"));
                }
                if (content.contains("Python")) {
                    result.addKnowledge(new Knowledge("发现Python高级应用模式", "PatternFinderPro"));
                }
                if (content.contains("AI")) {
                    result.addKnowledge(new Knowledge("发现AI前沿技术模式", "PatternFinderPro"));
                }

                return result;
            }
        });

        System.out.println("\n升级后工具链顺序: " + engine.getToolSequence());

        // 使用升级后的工具处理数据
        System.out.println("\n===== 处理第四份数据 (工具升级后) =====");
        DataSet data4 = new TextDataSet("Source4", "Python在AI领域的最新进展");
        KnowledgeSet result4 = engine.process(data4);
        result4.getAllKnowledge().forEach(System.out::println);

        // 查看知识关联
        System.out.println("\n===== 知识关联详情 =====");
        System.out.println(memory.getKnowledgeDetails(0)); // 第一份知识的分析结果
        System.out.println("\n" + memory.getKnowledgeDetails(2)); // 第一份知识的模式发现
        System.out.println("\n" + memory.getKnowledgeDetails(5)); // 第二份知识的分析结果

        // 记忆检索
        System.out.println("\n===== 'Python'相关记忆检索 =====");
        memory.searchMemory("Python").forEach(k ->
                System.out.println(k.toDetailedString(memory))
        );
    }
}