package life.iai.demo;

import java.util.*;
import java.util.stream.Collectors;

// ================== 核心基类定义 ==================
// ================== 核心基类定义结束 ==================

// 金融新闻数据集
class FinancialNews extends DataSet {
    private final String newsContent;
    private final Date publishDate;

    public FinancialNews(String source, String content, Date date) {
        super(source);
        this.newsContent = content;
        this.publishDate = date;
    }

    @Override
    public String getContent() {
        return String.format("[%tF] %s: %s", publishDate, source, newsContent);
    }

    public Date getPublishDate() {
        return publishDate;
    }
}

// 投资知识表示
class InvestmentKnowledge extends Knowledge {
    private final KnowledgeType type;
    private double confidence;
    // 添加公司名称字段
    private static String companyName;

    public static String getCompanyName() {
        return companyName;
    }

    public enum KnowledgeType {
        COMPANY_ANALYSIS,
        INDUSTRY_TREND,
        RISK_SIGNAL,
        INVESTMENT_RECOMMENDATION
    }

    public InvestmentKnowledge(String content, String source, KnowledgeType type, double confidence, String companyName) {
        super(content, source);
        this.type = type;
        this.confidence = Math.max(0, Math.min(1.0, confidence));
        this.companyName = companyName;
    }

    public KnowledgeType getType() {
        return type;
    }

    public double getConfidence() {
        return confidence;
    }

    public void updateConfidence(double delta) {
        this.confidence = Math.max(0, Math.min(1.0, confidence + delta));
    }

    @Override
    public String toString() {
        return String.format("%s [%.0f%%]: %s", type.name(), confidence * 100, super.getContent());
    }
}

// 公司分析工具
class CompanyAnalysisTool implements KnowledgeTool {
    private final Set<String> watchlist;

    public CompanyAnalysisTool(Set<String> companies) {
        this.watchlist = new HashSet<>(companies);
    }

    @Override
    public String getName() {
        return "CompanyAnalyzer";
    }

    @Override
    public KnowledgeSet process(DataSet input, KnowledgeSet context) {
        KnowledgeSet result = new KnowledgeSet();
        String content = input.getContent();

        // 检测关注列表中的公司
        for (String company : watchlist) {
            if (content.contains(company)) {
                double confidence = 0.7; // 基础置信度

                // 情感分析（简化版）
                if (content.contains("增长") || content.contains("利好")) confidence += 0.2;
                if (content.contains("下滑") || content.contains("风险")) confidence -= 0.15;

                String analysis = String.format("公司分析: %s - %s",
                        company, content.substring(0, Math.min(50, content.length())));

                result.addKnowledge(new InvestmentKnowledge(
                        analysis, "CompanyAnalyzer",
                        InvestmentKnowledge.KnowledgeType.COMPANY_ANALYSIS, confidence,
                        company  // 添加公司名称
                ));
            }
        }
        return result;
    }

    public void addCompany(String company) {
        watchlist.add(company);
    }
}

// 行业趋势工具
class IndustryTrendTool implements KnowledgeTool {
    private static final Map<String, String> INDUSTRY_KEYWORDS = Map.of(
            "科技", "AI|云计算|芯片|半导体",
            "金融", "银行|利率|货币政策|通胀",
            "能源", "石油|天然气|新能源|碳中和"
    );

    @Override
    public String getName() {
        return "IndustryTrendDetector";
    }

    @Override
    public KnowledgeSet process(DataSet input, KnowledgeSet context) {
        KnowledgeSet result = new KnowledgeSet();
        String content = input.getContent();

        for (Map.Entry<String, String> entry : INDUSTRY_KEYWORDS.entrySet()) {
            String industry = entry.getKey();
            String regex = entry.getValue();

            if (content.matches(".*(" + regex + ").*")) {
                // 计算趋势强度（简化版）
                int keywordCount = 0;
                for (String kw : regex.split("\\|")) {
                    if (content.contains(kw)) keywordCount++;
                }

                double confidence = 0.5 + (0.1 * keywordCount);
                String trend = String.format("行业趋势: [%s]领域出现(%d)个关键词", industry, keywordCount);

                result.addKnowledge(new InvestmentKnowledge(
                        trend, "TrendDetector",
                        InvestmentKnowledge.KnowledgeType.INDUSTRY_TREND, confidence,
                        InvestmentKnowledge.getCompanyName()
                ));
            }
        }
        return result;
    }
}

// 投资建议生成工具
class InvestmentAdviceTool implements KnowledgeTool {
    @Override
    public String getName() {
        return "InvestmentAdvisor";
    }

    @Override
    public KnowledgeSet process(DataSet input, KnowledgeSet context) {
        KnowledgeSet result = new KnowledgeSet();
        List<Knowledge> allKnowledge = context.getAllKnowledge();

        // 分析公司分析知识
        Map<String, Double> companyScores = new HashMap<>();
        for (Knowledge k : allKnowledge) {
            if (k instanceof InvestmentKnowledge) {
                InvestmentKnowledge ik = (InvestmentKnowledge) k;
                if (ik.getType() == InvestmentKnowledge.KnowledgeType.COMPANY_ANALYSIS) {
                    String company = ik.getContent().split(":")[1].trim().split(" ")[0];
                    companyScores.put(company, companyScores.getOrDefault(company, 0.0) + ik.getConfidence());
                }
            }
        }

        // 生成投资建议
        if (!companyScores.isEmpty()) {
            String bestCompany = Collections.max(companyScores.entrySet(), Map.Entry.comparingByValue()).getKey();
            double maxScore = companyScores.get(bestCompany);

            String advice = String.format("投资建议: 增持%s (信心指数: %.0f/100)",
                    bestCompany, maxScore * 100 / companyScores.size());

            result.addKnowledge(new InvestmentKnowledge(
                    advice, "InvestmentAdvisor",
                    InvestmentKnowledge.KnowledgeType.INVESTMENT_RECOMMENDATION,
                    Math.min(0.9, maxScore * 0.1),
                    InvestmentKnowledge.getCompanyName()
            ));
        }

        return result;
    }
}

// 反馈学习工具（根据市场表现调整置信度）
class FeedbackLearningTool implements KnowledgeTool {
    private final MemorySystem memorySystem;
    private final Map<Long, Double> knowledgePerformance = new HashMap<>();

    public FeedbackLearningTool(MemorySystem memorySystem) {
        this.memorySystem = memorySystem;
    }

    @Override
    public String getName() { return "FeedbackLearner"; }

    public void recordPerformance(long knowledgeId, double performance) {
        knowledgePerformance.put(knowledgeId, performance);
    }

    @Override
    public KnowledgeSet process(DataSet input, KnowledgeSet context) {
        KnowledgeSet result = new KnowledgeSet();

        // 从长期记忆中获取知识对象进行修改
        for (Long id : new ArrayList<>(knowledgePerformance.keySet())) {
            Knowledge k = memorySystem.getKnowledgeById(id);
            if (k instanceof InvestmentKnowledge ik) {
                double performance = knowledgePerformance.get(id);

                // 根据表现调整置信度
                double delta = (performance - 0.5) * 0.1;
                double newConfidence = Math.max(0, Math.min(1.0, ik.getConfidence() + delta));

                // 创建更新后的知识对象
                InvestmentKnowledge updated = new InvestmentKnowledge(
                        ik.getContent(), ik.getSource(), ik.getType(), newConfidence,
                        ik.getCompanyName()
                );
                updated.setId(ik.getId()); // 保持相同ID

                result.addKnowledge(updated);
                knowledgePerformance.remove(id);
            }
        }
        return result;
    }
}

// 金融知识记忆系统
class FinancialMemory extends MemorySystem {

    public List<InvestmentKnowledge> getInvestmentRecommendations() {
        return searchMemory("投资建议").stream()
                .filter(k -> k instanceof InvestmentKnowledge)
                .map(k -> (InvestmentKnowledge) k)
                .filter(k -> k.getType() == InvestmentKnowledge.KnowledgeType.INVESTMENT_RECOMMENDATION)
                .collect(Collectors.toList());
    }

    public List<InvestmentKnowledge> getCompanyAnalyses(String company) {
//        return semanticSearchMemory(company).stream()
//                .filter(k -> k instanceof InvestmentKnowledge)
//                .map(k -> (InvestmentKnowledge) k)
//                .filter(k -> k.getType() == InvestmentKnowledge.KnowledgeType.COMPANY_ANALYSIS)
//                .collect(Collectors.toList());
        // 使用新的公司索引查询
        return searchByCompany(company).stream()
                .filter(k -> k instanceof InvestmentKnowledge)
                .map(k -> (InvestmentKnowledge) k)
                .filter(k -> k.getType() == InvestmentKnowledge.KnowledgeType.COMPANY_ANALYSIS)
                .collect(Collectors.toList());

    }
}

// 金融分析引擎
class FinancialAnalysisEngine extends ThinkingEngine {
    private final FeedbackLearningTool feedbackTool;

    public FinancialAnalysisEngine(ToolChain toolChain, FinancialMemory memory) {
        super(toolChain, memory);
        this.feedbackTool = (FeedbackLearningTool) toolChain.getToolSequence().stream()
                .filter(t -> t.getName().equals("FeedbackLearner"))
                .findFirst()
                .orElse(null);
    }

    public void recordMarketPerformance(long knowledgeId, double performance) {
        if (feedbackTool != null) {
            feedbackTool.recordPerformance(knowledgeId, performance);
        }
    }

    // 获取最新投资建议
    public List<InvestmentKnowledge> getLatestRecommendations() {
        if (memory instanceof FinancialMemory) {
            return ((FinancialMemory) memory).getInvestmentRecommendations();
        }
        return Collections.emptyList();
    }
}

public class IntelligentInvestmentSystem {
    public static void main(String[] args) {
        // 初始化工具链和内存系统
        FinancialMemory memory = new FinancialMemory();
        // 初始化工具链
        ToolChain toolChain = new ToolChain();

        // 注意：将memory传递给FeedbackLearningTool
        FeedbackLearningTool feedbackTool = new FeedbackLearningTool(memory);
        toolChain.addTool(feedbackTool);

        // 设置关注的公司列表
        CompanyAnalysisTool companyTool = new CompanyAnalysisTool(new HashSet<>(Arrays.asList("腾讯", "阿里巴巴", "宁德时代", "茅台")));
        toolChain.addTool(companyTool);

        toolChain.addTool(new IndustryTrendTool());
        toolChain.addTool(new InvestmentAdviceTool());

        FinancialAnalysisEngine engine = new FinancialAnalysisEngine(toolChain, memory);

        // 模拟金融新闻输入
        List<FinancialNews> newsList = Arrays.asList(
                new FinancialNews("财经网", "腾讯发布新财报，游戏业务增长超预期，云服务稳步提升", new Date()),
                new FinancialNews("路透社", "新能源行业迎来政策利好，宁德时代宣布扩大产能", new Date()),
                new FinancialNews("华尔街日报", "全球通胀压力加剧，科技股普遍下跌，阿里巴巴受影响较大", new Date()),
                new FinancialNews("彭博社", "茅台发布高端新品，白酒行业复苏迹象明显", new Date())
        );

        System.out.println("====== 开始处理金融新闻 ======");
        for (int i = 0; i < newsList.size(); i++) {
            System.out.println("\n>>> 处理新闻 #" + (i + 1) + ": " + newsList.get(i).getContent());
            KnowledgeSet result = engine.process(newsList.get(i));

            System.out.println("生成知识:");
            result.getAllKnowledge().forEach(System.out::println);
        }

        // 获取投资建议
        System.out.println("\n====== 生成投资建议 ======");
        List<InvestmentKnowledge> recommendations = engine.getLatestRecommendations();
        if (recommendations.isEmpty()) {
            System.out.println("没有生成投资建议");
        } else {
            recommendations.forEach(System.out::println);
        }

        // 模拟市场反馈（假设腾讯表现良好，阿里表现不佳）
        System.out.println("\n====== 模拟市场反馈 ======");
        List<Knowledge> adviceList = memory.searchMemory("投资建议");
        if (adviceList.isEmpty()) {
            System.out.println("没有找到投资建议记录");
        } else {
            for (Knowledge k : adviceList) {
                if (k instanceof InvestmentKnowledge advice) {
                    double performance = advice.getContent().contains("腾讯") ? 0.8 : 0.3;
                    engine.recordMarketPerformance(advice.getId(), performance);
                    System.out.println("记录表现: " + advice.getContent() + " -> " + (performance * 100) + "%");
                }
            }
        }

        // 重新处理数据以应用反馈学习
        System.out.println("\n====== 应用反馈学习 ======");
        KnowledgeSet feedbackResult = engine.process(newsList.get(0)); // 重新处理第一条新闻

        // 输出反馈学习结果
        System.out.println("反馈学习生成的知识:");
        feedbackResult.getAllKnowledge().forEach(System.out::println);

        // 显示更新后的知识置信度
        System.out.println("\n更新后的腾讯分析:");
        List<InvestmentKnowledge> tencentAnalyses = memory.getCompanyAnalyses("腾讯");
        if (tencentAnalyses.isEmpty()) {
            System.out.println("没有腾讯的分析记录");
        } else {
            tencentAnalyses.forEach(k ->
                    System.out.println(k.getContent() + " [置信度: " + (int) (k.getConfidence() * 100) + "%]")
            );
        }

        System.out.println("\n更新后的阿里巴巴分析:");
        List<InvestmentKnowledge> alibabaAnalyses = memory.getCompanyAnalyses("阿里巴巴");
        if (alibabaAnalyses.isEmpty()) {
            System.out.println("没有阿里巴巴的分析记录");
        } else {
            alibabaAnalyses.forEach(k ->
                    System.out.println(k.getContent() + " [置信度: " + (int) (k.getConfidence() * 100) + "%]")
            );
        }

        // 添加新公司到关注列表
        companyTool.addCompany("美团");
        FinancialNews newNews = new FinancialNews("财新网", "美团外卖业务增长强劲，股价创新高", new Date());

        System.out.println("\n====== 处理新公司新闻 ======");
        KnowledgeSet newResult = engine.process(newNews);
        newResult.getAllKnowledge().forEach(System.out::println);

        System.out.println("\n====== 最终投资建议 ======");
        List<InvestmentKnowledge> finalRecommendations = engine.getLatestRecommendations();
        if (finalRecommendations.isEmpty()) {
            System.out.println("没有投资建议");
        } else {
            finalRecommendations.forEach(System.out::println);
        }
    }
}
