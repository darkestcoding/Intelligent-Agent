package group5;

import genius.core.Bid;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.uncertainty.BidRanking;
import genius.core.uncertainty.UserModel;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;

import java.util.*;


public class GeneticAlgorithm {
    public static long overTime = 58 * 1000;
    public static long beginTime;
    private Random random = new Random();
    private UserModel userModel;
    public BidRanking bidRanking;
    public List<Bid> rankedBidList = new ArrayList<>();
    public List<AbstractUtilitySpace> population = new ArrayList<>();  // 一个种群里包括n个utility space
    public List<AbstractUtilitySpace> nextPopulation;
    private double optimumFitness = -1;
    private AbstractUtilitySpace optimumSpace;
    // 1500
    public int populationNum = 1500;
    // 变异概率pm, [0.001 - 0.1]. 随着迭代次数的增加变小0.1 0.001
    double pm = 0.15;
    double discountPm = 0.001;
    // 在crossover的时候父代和母代通过一定的概率交叉，通过rank来计算这个概率
    public List<Double> fitnessRankFotNextPopulation = new ArrayList<>();

    public GeneticAlgorithm(UserModel userModel) {
        beginTime = System.currentTimeMillis();
        this.userModel = userModel;

        bidRanking = userModel.getBidRanking();
        int bidRankingSize = bidRanking.getSize();
        List<Bid> bidRankingList = new ArrayList<>();
        for (Bid bid : bidRanking) {
            bidRankingList.add(bid);
        }
        if (bidRankingList.size() <= 400) {
            for (int i = 0; i < bidRankingSize; i++) {
                rankedBidList.add(bidRankingList.get(i));
            }
        }
//        else if (bidRankingList.size() <= 800) {
//            // 假设有500个bid
//            int jumpNum = bidRankingSize - 400;  // 为了能够刚好取到400个bid，那么需要跳过100个bid
//            int roundToJump = bidRankingSize / jumpNum;  // 每5轮跳过一个bid
//            int step = bidRanking.getSize() / 400;
//            int temp = step;
//            for (int i = 0; i < bidRanking.getSize(); i = i + step) {
////                System.out.println(i);
//                if ((i + 1) % roundToJump == 0) {  // 需要跳了
//                    step++;
//                } else {  // 不需要跳
//                    if (step > temp) {
//                        step--;
//                    }
//                }
//                rankedBidList.add(bidRankingList.get(i)); // 实际上取了401个bid
//            }
//        }
        else {
            int step = bidRanking.getSize() / 400;
            for (int i = 0; i < bidRankingSize; i = i + step) {
                rankedBidList.add(bidRankingList.get(i));
            }
        }
//        System.out.println(rankedBidList.size());

    }

    /**
     * 随机生成 Utility Space
     *
     * @return
     * @throws Exception
     */
    public AbstractUtilitySpace generateUtilitySpace() {
        AdditiveUtilitySpaceFactory additiveUtilitySpaceFactory = new AdditiveUtilitySpaceFactory(userModel.getDomain());
        List<Issue> issues = additiveUtilitySpaceFactory.getDomain().getIssues();
        for (Issue issue : issues) {
            additiveUtilitySpaceFactory.setWeight(issue, random.nextDouble());
            IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
            for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
                additiveUtilitySpaceFactory.setUtility(issue, valueDiscrete, random.nextDouble());
            }
        }
        additiveUtilitySpaceFactory.normalizeWeights();
        return additiveUtilitySpaceFactory.getUtilitySpace();
    }

    /**
     * 生成指定数量的种群
     *
     * @param num
     * @throws Exception
     */
    public void initPopulation(int num) {
        for (int i = 0; i < num; i++) {
            population.add(generateUtilitySpace());
        }
    }

    /**
     * 适应度函数通过个体特征判断个体的适应度
     *
     * @param population
     * @return
     */
    public List<Double> fitness(List<AbstractUtilitySpace> population) {
        List<Double> fitnessList = new ArrayList<>();
        Map<Integer, Double> map = new HashMap<>();

        for (int i = 0; i < population.size(); i++) {
            Map<Integer, Double> estimatedUtility = new HashMap<>();  // <真实的bid rank， 预测的utility>

            // 1、 根据排序好的bid计算它在某个utility space里utility
            for (int j = 0; j < rankedBidList.size(); j++) {
                Bid bid = rankedBidList.get(j);
                estimatedUtility.put(j, population.get(i).getUtility(bid));
            }

            // 2、 对计算好的utility进行升序排序
            List<Map.Entry<Integer, Double>> utilityList = new ArrayList<>(estimatedUtility.entrySet());
            Collections.sort(utilityList, new Comparator<Map.Entry<Integer, Double>>() {
                @Override
                public int compare(Map.Entry<Integer, Double> o1, Map.Entry<Integer, Double> o2) {
                    if (o1 == null && o2 == null) {
                        return 0;
                    }
                    if (o1 == null) {
                        return -1;
                    }
                    if (o2 == null) {
                        return 1;
                    }
                    if (o1.getValue() < o2.getValue())
                        return -1;
                    else if (o1.getValue() > o2.getValue()) {
                        return 1;
                    } else
                        return 0;
                }
            });

            // 3、 计算rank的差值   E = 1/N|Rank_est - Rank_real|
            double error = 0.0;
            for (int k = 0; k < rankedBidList.size(); k++) {
                int rankDiff = Math.abs(utilityList.get(k).getKey() - k);
                error += rankDiff;
            }

            int mean = 0;
            double sigma = rankedBidList.size() * 0.5;
            // 4、 打分 score
            double fitness = 32 * Math.exp(-Math.pow(error / rankedBidList.size() - mean, 2) / (2 * sigma * sigma));
            fitnessList.add(fitness);
            map.put(i, fitness);
        }

        // 加入值使最坏个体仍有繁殖的可能
        List<Map.Entry<Integer, Double>> fitnessSortedList = new ArrayList<>(map.entrySet());
        Collections.sort(fitnessSortedList, new Comparator<Map.Entry<Integer, Double>>() {
            @Override
            public int compare(Map.Entry<Integer, Double> o1, Map.Entry<Integer, Double> o2) {
                if (o1 == null && o2 == null) {
                    return 0;
                }
                if (o1 == null) {
                    return -1;
                }
                if (o2 == null) {
                    return 1;
                }
                if (o1.getValue() < o2.getValue())
                    return -1;
                else if (o1.getValue() > o2.getValue()) {
                    return 1;
                } else
                    return 0;
            }
        });


        return fitnessList;
    }


    public List<AbstractUtilitySpace> rouletteWheelSelection(List<AbstractUtilitySpace> population, List<Double> fitnessList, int selectionSize) {
        int eliteNum = 10;
        double totalFitness = 0.0;
        // nextPopulation 已经是一个public成员了，这里可以不用重复定义nextPopulation，直接给成员变量赋值就行。
        List<AbstractUtilitySpace> nextPopulation = new ArrayList<>();

        List<Double> temp = new ArrayList<>();
        for (int i = 0; i < fitnessList.size(); i++) {
            temp.add(fitnessList.get(i));
        }
        // get the optimum fitness each generation.
        if (fitnessList.get(temp.indexOf(Collections.max(temp))) > optimumFitness) {
            optimumFitness = fitnessList.get(temp.indexOf(Collections.max(temp)));
            optimumSpace = population.get(temp.indexOf(Collections.max(temp)));
        }
        for (int i = 0; i < eliteNum; i++) {
            nextPopulation.add(population.get(temp.indexOf(Collections.max(temp))));
            fitnessRankFotNextPopulation.add(fitnessList.get(temp.indexOf(Collections.max(temp))));
            temp.set(temp.indexOf(Collections.max(temp)), (double) -10000);
        }

        for (int i = 0; i < fitnessList.size(); i++) {
            totalFitness += fitnessList.get(i);
        }

        List<Double> relativeFitness = new ArrayList<>();
        for (int i = 0; i < fitnessList.size(); i++) {
            relativeFitness.add(fitnessList.get(i) / totalFitness);
        }

        double cumulativeFitness = 0.0;
        for (int i = 0; i < selectionSize; i++) {
            for (int j = 0; j < population.size(); j++) {
                cumulativeFitness += relativeFitness.get(j);
                if (random.nextDouble() < cumulativeFitness) {
//                    System.out.println(j);
                    nextPopulation.add(population.get(j));
                    // 把next generation中的个体的fitness记录一下, 前提是
                    fitnessRankFotNextPopulation.add(fitnessList.get(j));
                    break;
                }
            }
        }


        return nextPopulation;
    }


    //        算术交叉（Arithmetic Crossover）：由两个个体的线性组合而产生出两个新的个体。
    public AdditiveUtilitySpace crossover(AdditiveUtilitySpace parentOne, AdditiveUtilitySpace parentTwo, double fitness1, double fitness2) throws Exception {
        //1、计算 crossover 时两个亲本基因的权重。Fitness高的亲本权重大。
        double weightOne = fitness1 / (fitness1 + fitness2);
        double weightTwo = 1 - weightOne;
        double gamma = 0.8;
        int count = 1;
        double alpha = 0.0;
        if (pm > 0.04) {
            pm = pm - discountPm;
        }
        //2、初始化子代的UtilitySpace
        AdditiveUtilitySpaceFactory child = new AdditiveUtilitySpaceFactory(userModel.getDomain());
        List<Issue> issues = child.getDomain().getIssues();

        for (Issue issue : issues) {
            // 计算子代issue的weight
            double parent1Weight = parentOne.getWeight(issue);
            double parent2Weight = parentTwo.getWeight(issue);
            // 考虑变异情况
            if (random.nextDouble() > pm) {
                alpha = Math.pow(weightOne, Math.pow(gamma, count - 1));
                child.setWeight(issue, weightOne * parent1Weight + weightTwo * parent2Weight);
//                if (random.nextDouble() > 0.5) {
//                    child.setWeight(issue, alpha * parent1Weight - (1 - alpha) * parent2Weight);
//                }else{
//                    child.setWeight(issue, alpha * parent1Weight + (1 - alpha) * parent2Weight);
//                }
                // 更新alpha
            } else {
                child.setWeight(issue, random.nextDouble());
            }
            // 计算子代的eval
            IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
            int issueNumber = issue.getNumber();
            EvaluatorDiscrete parent1Evaluator = (EvaluatorDiscrete) parentOne.getEvaluator(issueNumber);
            EvaluatorDiscrete parent2Evaluator = (EvaluatorDiscrete) parentTwo.getEvaluator(issueNumber);
            for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
                // 考虑变异情况
                if (random.nextDouble() > pm) {
                    double childEvaluation = weightOne * parent1Evaluator.getEvaluation(valueDiscrete) + weightTwo * parent2Evaluator.getEvaluation(valueDiscrete);
                    child.setUtility(issue, valueDiscrete, childEvaluation);
                } else {
                    child.setUtility(issue, valueDiscrete, random.nextDouble());
                }
//                // 启发式变异 可以用的
//                if (random.nextDouble() > pm) {
//                    alpha = Math.pow(weightOne, Math.pow(gamma, count - 1));
//                    if (random.nextDouble() > 0.5) {
//                        double childEvaluation = alpha * parent1Evaluator.getEvaluation(valueDiscrete) +
//                                (1 - alpha) * parent2Evaluator.getEvaluation(valueDiscrete);
//                        child.setUtility(issue, valueDiscrete, childEvaluation);
//                    } else {
//                        double childEvaluation = alpha * parent1Evaluator.getEvaluation(valueDiscrete) -
//                                (1 - alpha) * parent2Evaluator.getEvaluation(valueDiscrete);
//                        if (childEvaluation < 0) childEvaluation = 0.01;
//                        child.setUtility(issue, valueDiscrete, childEvaluation);
//                    }
//                    count++;
//                    // 更新alpha
//                } else {
//                    child.setUtility(issue, valueDiscrete, random.nextDouble());
//                }
            }

        }
        child.normalizeWeights();
        return child.getUtilitySpace();
    }

    public void calculate(AdditiveUtilitySpace additiveUtilitySpace) {
        List<Double> utilityList = new ArrayList<>();
        for (Bid bid : rankedBidList) {
            utilityList.add(additiveUtilitySpace.getUtility(bid));   //计算在当前空间下，每个bidRanking的实际效用是多少。并且放入utilityList中。
        }                                                             //注意，此时的utilityList的索引和bidRanking的索引是相同的。我们需要利用这个存放在TreeMap中
        TreeMap<Integer, Double> utilityRank = new TreeMap<>();   //构建treeMap，一个存放一下当前的索引，一个存放对应索引的utility。

        for (int i = 0; i < utilityList.size(); i++) {   //这里对utility进行遍历，将索引和效用存放在TreeMap中。
            utilityRank.put(i, utilityList.get(i));
        }

        //4. 此时我们需要根据TreeMap的值进行排序（值中存放的是效用值）
        Comparator<Map.Entry<Integer, Double>> valueComparator = Comparator.comparingDouble(Map.Entry::getValue);
        // map转换成list进行排序
        List<Map.Entry<Integer, Double>> listRank = new ArrayList<>(utilityRank.entrySet());
        // 排序
        Collections.sort(listRank, valueComparator);

        //用以上的方法，TreeMap此时就被转换成了List。这tm什么方法我也很烦躁。。
        //list现在长这个样子。[100=0.3328030236029489, 144=0.33843867914476017, 82=0.35366230775310603, 68=0.39994535024458255, 25=0.4407324473062739, 119=0.45895568095691974,
        //不过这也有个好处。就是列表的索引值，可以表示为utilityList的索引值。
        int sum = 0;
        int error = 0;
        for (int i = 0; i < listRank.size(); i++) {
            int gap = Math.abs(listRank.get(i).getKey() - i);  //5. 这里的i其实可以对应utilityList的索引i。假设i=1.此时在utilityList中的效用应该是最低值。
            error += gap * gap;
            if (listRank.get(i).getKey() - i != 0) {
                sum++;

            }
//            System.out.print(listRank.get(i).getKey()+" ");
//            System.out.print(listRank.get(i).getValue());
//            System.out.println();
        }                                             //但是，在listRank中，效用最低的值对应的index竟然是100。那说明，这个效用空间在第一个位置差了很大。
        // 同理，如果listRank中的每一个键能正好接近或者等于它所在的索引数，那么说明这个效用空间分的就很对。
//        System.out.println("-------------------------------------------------");
        System.out.println("Numbers of false ranking:" + sum);


    }


    public AbstractUtilitySpace runGeneticAlgorithm() throws Exception {
        initPopulation(populationNum);
        List<Double> fitnessList = new ArrayList<>();
        // 繁殖n代
        for (int n = 0; n < 1000; n++) {
            long nowtime = System.currentTimeMillis();
            if (nowtime - beginTime > overTime){
                break;
            }
            nextPopulation = new ArrayList<>();
            // 计算第n代的fitness们并储存在fitnessList中

            fitnessList = fitness(population);

            // 轮盘赌，选择获得交配权的个体
            List<AbstractUtilitySpace> authorityPopulation = rouletteWheelSelection(population, fitnessList, 550);

            // 随机选择具有交配权的两个个体进行交配,选择i个
            int size = authorityPopulation.size();

            for (int i = 0; i < 200; i++) {
                int parentOneIndex = random.nextInt(size);
                int parentTwoIndex = random.nextInt(size);
                while (parentOneIndex == parentTwoIndex) {
                    parentTwoIndex = random.nextInt(size);
                }

                AdditiveUtilitySpace parentOne = (AdditiveUtilitySpace) authorityPopulation.get(parentOneIndex);
                AdditiveUtilitySpace parentTwo = (AdditiveUtilitySpace) authorityPopulation.get(parentTwoIndex);
                AdditiveUtilitySpace child = crossover(parentOne, parentTwo, fitnessRankFotNextPopulation.get(parentOneIndex),
                        fitnessRankFotNextPopulation.get(parentTwoIndex));
                authorityPopulation.add(child);
            }

            population = authorityPopulation;
            fitnessRankFotNextPopulation.clear();

            List<Double> fitnessList1 = fitness(population);
            double max = 0.0;
            int index = 0;
            for (int i = 0; i < fitnessList1.size(); i++) {
                if (max < fitnessList1.get(i)) {
                    max = fitnessList1.get(i);
                    index = i;
                }
            }
            calculate((AdditiveUtilitySpace) population.get(index));
        }

//        calculate((AdditiveUtilitySpace) population.get(index));
//        double fitness = Collections.max(fitnessList);
//        if (fitness > optimumFitness) {
//            int bestIndex = fitnessList.indexOf(fitness);
//            return population.get(bestIndex);
//        } else {
//            return optimumSpace;
//        }
        return optimumSpace;
    }


}
