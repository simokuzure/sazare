-- 默认用户初始化数据

insert into users (
    user_code,
    nickname,
    user_type,
    enabled,
    deleted,
    created_at,
    updated_at
)
values (
    'LOCAL_DEFAULT',
    '本地用户',
    'LOCAL',
    true,
    false,
    current_timestamp,
    current_timestamp
)
on conflict (user_code) do update set
    nickname = excluded.nickname,
    user_type = excluded.user_type,
    enabled = true,
    deleted = false,
    updated_at = current_timestamp;

-- 标签初始化数据

-- 初始化固定标签数据
drop table if exists seed_tags;

create temporary table seed_tags (
    tag_type varchar(32) not null,
    parent_code varchar(64) null,
    code varchar(64) not null,
    name varchar(64) not null,
    description varchar(255) null,
    sort_order integer not null
);

insert into seed_tags (tag_type, parent_code, code, name, description, sort_order) values
('SCENE', null, 'DAILY_LIFE', '日常生活', '场景一级标签', 1000),
('SCENE', 'DAILY_LIFE', 'DAILY_LIFE_HOME', '居家', '日常生活场景标签', 1010),
('SCENE', 'DAILY_LIFE', 'DAILY_LIFE_DAILY_TASKS', '日常事务', '日常生活场景标签', 1020),
('SCENE', 'DAILY_LIFE', 'DAILY_LIFE_NEIGHBORHOOD', '邻里', '日常生活场景标签', 1030),
('SCENE', 'DAILY_LIFE', 'DAILY_LIFE_WEATHER', '天气', '日常生活场景标签', 1040),
('SCENE', 'DAILY_LIFE', 'DAILY_LIFE_DATE_TIME', '时间日期', '日常生活场景标签', 1050),
('SCENE', 'DAILY_LIFE', 'DAILY_LIFE_PERSONAL_SCHEDULE', '个人安排', '日常生活场景标签', 1060),
('SCENE', null, 'SOCIAL', '社交交流', '场景一级标签', 2000),
('SCENE', 'SOCIAL', 'SOCIAL_FRIENDS', '朋友交流', '社交交流场景标签', 2010),
('SCENE', 'SOCIAL', 'SOCIAL_ACQUAINTANCES', '熟人交流', '社交交流场景标签', 2020),
('SCENE', 'SOCIAL', 'SOCIAL_FIRST_MEETING', '初次见面', '社交交流场景标签', 2030),
('SCENE', 'SOCIAL', 'SOCIAL_GATHERING', '聚会', '社交交流场景标签', 2040),
('SCENE', 'SOCIAL', 'SOCIAL_VISIT', '拜访', '社交交流场景标签', 2050),
('SCENE', 'SOCIAL', 'SOCIAL_ONLINE', '线上交流', '社交交流场景标签', 2060),
('SCENE', null, 'EDUCATION', '学校学习', '场景一级标签', 3000),
('SCENE', 'EDUCATION', 'EDUCATION_CLASSROOM', '课堂', '学校学习场景标签', 3010),
('SCENE', 'EDUCATION', 'EDUCATION_TEACHER_STUDENT', '师生交流', '学校学习场景标签', 3020),
('SCENE', 'EDUCATION', 'EDUCATION_CLASSMATES', '同学交流', '学校学习场景标签', 3030),
('SCENE', 'EDUCATION', 'EDUCATION_EXAM', '考试', '学校学习场景标签', 3040),
('SCENE', 'EDUCATION', 'EDUCATION_HOMEWORK', '作业', '学校学习场景标签', 3050),
('SCENE', 'EDUCATION', 'EDUCATION_SELF_STUDY', '自习', '学校学习场景标签', 3060),
('SCENE', 'EDUCATION', 'EDUCATION_SCHOOL_AFFAIRS', '学校事务', '学校学习场景标签', 3070),
('SCENE', null, 'WORK', '工作职场', '场景一级标签', 4000),
('SCENE', 'WORK', 'WORK_COLLEAGUES', '同事交流', '工作职场场景标签', 4010),
('SCENE', 'WORK', 'WORK_SUPERVISOR_SUBORDINATE', '上下级交流', '工作职场场景标签', 4020),
('SCENE', 'WORK', 'WORK_MEETING', '会议', '工作职场场景标签', 4030),
('SCENE', 'WORK', 'WORK_REPORT', '工作汇报', '工作职场场景标签', 4040),
('SCENE', 'WORK', 'WORK_TASK_COMMUNICATION', '任务沟通', '工作职场场景标签', 4050),
('SCENE', 'WORK', 'WORK_SOCIALIZING', '职场社交', '工作职场场景标签', 4060),
('SCENE', 'WORK', 'WORK_JOB_INTERVIEW', '求职面试', '工作职场场景标签', 4070),
('SCENE', null, 'PART_TIME_JOB', '兼职', '场景一级标签', 5000),
('SCENE', 'PART_TIME_JOB', 'PART_TIME_JOB_SHIFT', '排班', '兼职场景标签', 5010),
('SCENE', 'PART_TIME_JOB', 'PART_TIME_JOB_LEAVE_REQUEST', '请假', '兼职场景标签', 5020),
('SCENE', 'PART_TIME_JOB', 'PART_TIME_JOB_WORK_CONFIRMATION', '工作确认', '兼职场景标签', 5030),
('SCENE', 'PART_TIME_JOB', 'PART_TIME_JOB_MANAGER_COMMUNICATION', '店长沟通', '兼职场景标签', 5040),
('SCENE', 'PART_TIME_JOB', 'PART_TIME_JOB_COWORKER_COOPERATION', '同事协作', '兼职场景标签', 5050),
('SCENE', 'PART_TIME_JOB', 'PART_TIME_JOB_CUSTOMER_SERVICE', '顾客接待', '兼职场景标签', 5060),
('SCENE', null, 'SHOPPING', '购物消费', '场景一级标签', 6000),
('SCENE', 'SHOPPING', 'SHOPPING_PRODUCT_INQUIRY', '商品询问', '购物消费场景标签', 6010),
('SCENE', 'SHOPPING', 'SHOPPING_PRODUCT_SELECTION', '商品选择', '购物消费场景标签', 6020),
('SCENE', 'SHOPPING', 'SHOPPING_TRY_ON', '试用试穿', '购物消费场景标签', 6030),
('SCENE', 'SHOPPING', 'SHOPPING_CHECKOUT', '结账', '购物消费场景标签', 6040),
('SCENE', 'SHOPPING', 'SHOPPING_RETURN_EXCHANGE', '退换货', '购物消费场景标签', 6050),
('SCENE', 'SHOPPING', 'SHOPPING_AFTER_SALES', '售后服务', '购物消费场景标签', 6060),
('SCENE', null, 'DINING', '餐饮', '场景一级标签', 7000),
('SCENE', 'DINING', 'DINING_ENTERING', '入店', '餐饮场景标签', 7010),
('SCENE', 'DINING', 'DINING_ORDERING', '点餐', '餐饮场景标签', 7020),
('SCENE', 'DINING', 'DINING_EATING', '用餐', '餐饮场景标签', 7030),
('SCENE', 'DINING', 'DINING_ADDITIONAL_ORDER', '追加点单', '餐饮场景标签', 7040),
('SCENE', 'DINING', 'DINING_CHECKOUT', '结账', '餐饮场景标签', 7050),
('SCENE', 'DINING', 'DINING_RESERVATION', '预约', '餐饮场景标签', 7060),
('SCENE', 'DINING', 'DINING_TAKEOUT_DELIVERY', '外卖', '餐饮场景标签', 7070),
('SCENE', null, 'TRANSPORT', '交通出行', '场景一级标签', 8000),
('SCENE', 'TRANSPORT', 'TRANSPORT_DIRECTIONS', '问路', '交通出行场景标签', 8010),
('SCENE', 'TRANSPORT', 'TRANSPORT_WALKING', '步行', '交通出行场景标签', 8020),
('SCENE', 'TRANSPORT', 'TRANSPORT_BUS', '公交', '交通出行场景标签', 8030),
('SCENE', 'TRANSPORT', 'TRANSPORT_TRAIN', '电车', '交通出行场景标签', 8040),
('SCENE', 'TRANSPORT', 'TRANSPORT_SUBWAY', '地铁', '交通出行场景标签', 8050),
('SCENE', 'TRANSPORT', 'TRANSPORT_SHINKANSEN', '新干线', '交通出行场景标签', 8060),
('SCENE', 'TRANSPORT', 'TRANSPORT_TAXI', '出租车', '交通出行场景标签', 8070),
('SCENE', 'TRANSPORT', 'TRANSPORT_DRIVING', '驾车', '交通出行场景标签', 8080),
('SCENE', 'TRANSPORT', 'TRANSPORT_STATION_AIRPORT', '车站机场', '交通出行场景标签', 8090),
('SCENE', null, 'TRAVEL', '旅行住宿', '场景一级标签', 9000),
('SCENE', 'TRAVEL', 'TRAVEL_ITINERARY', '行程安排', '旅行住宿场景标签', 9010),
('SCENE', 'TRAVEL', 'TRAVEL_SIGHTSEEING', '观光', '旅行住宿场景标签', 9020),
('SCENE', 'TRAVEL', 'TRAVEL_HOTEL_CHECKIN', '酒店入住', '旅行住宿场景标签', 9030),
('SCENE', 'TRAVEL', 'TRAVEL_HOTEL_SERVICE', '酒店服务', '旅行住宿场景标签', 9040),
('SCENE', 'TRAVEL', 'TRAVEL_CHECKOUT', '退房', '旅行住宿场景标签', 9050),
('SCENE', 'TRAVEL', 'TRAVEL_LUGGAGE', '行李', '旅行住宿场景标签', 9060),
('SCENE', null, 'HEALTHCARE', '医疗健康', '场景一级标签', 10000),
('SCENE', 'HEALTHCARE', 'HEALTHCARE_REGISTRATION', '挂号', '医疗健康场景标签', 10010),
('SCENE', 'HEALTHCARE', 'HEALTHCARE_SYMPTOMS', '描述症状', '医疗健康场景标签', 10020),
('SCENE', 'HEALTHCARE', 'HEALTHCARE_CONSULTATION', '问诊', '医疗健康场景标签', 10030),
('SCENE', 'HEALTHCARE', 'HEALTHCARE_EXAMINATION', '检查', '医疗健康场景标签', 10040),
('SCENE', 'HEALTHCARE', 'HEALTHCARE_PHARMACY', '药店', '医疗健康场景标签', 10050),
('SCENE', 'HEALTHCARE', 'HEALTHCARE_PHYSICAL_CONDITION', '身体状态', '医疗健康场景标签', 10060),
('SCENE', null, 'PUBLIC_SERVICE', '公共服务', '场景一级标签', 11000),
('SCENE', 'PUBLIC_SERVICE', 'PUBLIC_SERVICE_GOVERNMENT_OFFICE', '政府机关', '公共服务场景标签', 11010),
('SCENE', 'PUBLIC_SERVICE', 'PUBLIC_SERVICE_PROCEDURES', '手续办理', '公共服务场景标签', 11020),
('SCENE', 'PUBLIC_SERVICE', 'PUBLIC_SERVICE_DOCUMENTS', '证件', '公共服务场景标签', 11030),
('SCENE', 'PUBLIC_SERVICE', 'PUBLIC_SERVICE_POST_OFFICE', '邮局', '公共服务场景标签', 11040),
('SCENE', 'PUBLIC_SERVICE', 'PUBLIC_SERVICE_POLICE', '警察', '公共服务场景标签', 11050),
('SCENE', 'PUBLIC_SERVICE', 'PUBLIC_SERVICE_FACILITIES', '公共设施', '公共服务场景标签', 11060),
('SCENE', null, 'FINANCE', '金融', '场景一级标签', 12000),
('SCENE', 'FINANCE', 'FINANCE_BANK', '银行', '金融场景标签', 12010),
('SCENE', 'FINANCE', 'FINANCE_ACCOUNT_OPENING', '开户', '金融场景标签', 12020),
('SCENE', 'FINANCE', 'FINANCE_TRANSFER', '转账', '金融场景标签', 12030),
('SCENE', 'FINANCE', 'FINANCE_WITHDRAWAL', '取款', '金融场景标签', 12040),
('SCENE', 'FINANCE', 'FINANCE_PAYMENT', '支付', '金融场景标签', 12050),
('SCENE', 'FINANCE', 'FINANCE_BILL', '账单', '金融场景标签', 12060),
('SCENE', 'FINANCE', 'FINANCE_INSURANCE', '保险', '金融场景标签', 12070),
('SCENE', null, 'HOUSING', '居住', '场景一级标签', 13000),
('SCENE', 'HOUSING', 'HOUSING_SEARCH', '找房', '居住场景标签', 13010),
('SCENE', 'HOUSING', 'HOUSING_VIEWING', '看房', '居住场景标签', 13020),
('SCENE', 'HOUSING', 'HOUSING_CONTRACT', '签约', '居住场景标签', 13030),
('SCENE', 'HOUSING', 'HOUSING_RENT', '房租', '居住场景标签', 13040),
('SCENE', 'HOUSING', 'HOUSING_PROPERTY_MANAGEMENT', '物业', '居住场景标签', 13050),
('SCENE', 'HOUSING', 'HOUSING_REPAIR', '维修', '居住场景标签', 13060),
('SCENE', 'HOUSING', 'HOUSING_MOVING', '搬家', '居住场景标签', 13070),
('SCENE', null, 'COMMUNICATION', '通信网络', '场景一级标签', 14000),
('SCENE', 'COMMUNICATION', 'COMMUNICATION_PHONE', '电话', '通信网络场景标签', 14010),
('SCENE', 'COMMUNICATION', 'COMMUNICATION_TEXT_MESSAGE', '短信', '通信网络场景标签', 14020),
('SCENE', 'COMMUNICATION', 'COMMUNICATION_EMAIL', '邮件', '通信网络场景标签', 14030),
('SCENE', 'COMMUNICATION', 'COMMUNICATION_NETWORK', '网络', '通信网络场景标签', 14040),
('SCENE', 'COMMUNICATION', 'COMMUNICATION_MOBILE_PHONE', '手机', '通信网络场景标签', 14050),
('SCENE', 'COMMUNICATION', 'COMMUNICATION_DELIVERY_LOGISTICS', '快递物流', '通信网络场景标签', 14060),
('SCENE', null, 'LEISURE', '娱乐休闲', '场景一级标签', 15000),
('SCENE', 'LEISURE', 'LEISURE_HOBBIES', '兴趣爱好', '娱乐休闲场景标签', 15010),
('SCENE', 'LEISURE', 'LEISURE_GAMES', '游戏', '娱乐休闲场景标签', 15020),
('SCENE', 'LEISURE', 'LEISURE_ANIME_FILM', '动漫影视', '娱乐休闲场景标签', 15030),
('SCENE', 'LEISURE', 'LEISURE_MUSIC', '音乐', '娱乐休闲场景标签', 15040),
('SCENE', 'LEISURE', 'LEISURE_SPORTS', '运动', '娱乐休闲场景标签', 15050),
('SCENE', 'LEISURE', 'LEISURE_ACTIVITIES', '活动', '娱乐休闲场景标签', 15060),
('SCENE', null, 'RELATIONSHIP', '人际关系', '场景一级标签', 16000),
('SCENE', 'RELATIONSHIP', 'RELATIONSHIP_FAMILY', '家庭', '人际关系场景标签', 16010),
('SCENE', 'RELATIONSHIP', 'RELATIONSHIP_FRIENDS', '朋友', '人际关系场景标签', 16020),
('SCENE', 'RELATIONSHIP', 'RELATIONSHIP_ROMANCE', '恋爱', '人际关系场景标签', 16030),
('SCENE', 'RELATIONSHIP', 'RELATIONSHIP_COLLEAGUES', '同事', '人际关系场景标签', 16040),
('SCENE', 'RELATIONSHIP', 'RELATIONSHIP_SENIOR_JUNIOR', '前后辈', '人际关系场景标签', 16050),
('SCENE', 'RELATIONSHIP', 'RELATIONSHIP_CONFLICT', '人际矛盾', '人际关系场景标签', 16060),
('SCENE', null, 'BUSINESS', '商务正式', '场景一级标签', 17000),
('SCENE', 'BUSINESS', 'BUSINESS_CLIENT_COMMUNICATION', '客户交流', '商务正式场景标签', 17010),
('SCENE', 'BUSINESS', 'BUSINESS_MEETING', '商务会议', '商务正式场景标签', 17020),
('SCENE', 'BUSINESS', 'BUSINESS_EMAIL', '商务邮件', '商务正式场景标签', 17030),
('SCENE', 'BUSINESS', 'BUSINESS_RECEPTION', '接待', '商务正式场景标签', 17040),
('SCENE', 'BUSINESS', 'BUSINESS_APPOINTMENT', '预约', '商务正式场景标签', 17050),
('SCENE', 'BUSINESS', 'BUSINESS_NEGOTIATION', '谈判', '商务正式场景标签', 17060),
('SCENE', 'BUSINESS', 'BUSINESS_COOPERATION', '合作', '商务正式场景标签', 17070),
('SCENE', null, 'EMERGENCY', '紧急情况', '场景一级标签', 18000),
('SCENE', 'EMERGENCY', 'EMERGENCY_HELP_REQUEST', '求助', '紧急情况场景标签', 18010),
('SCENE', 'EMERGENCY', 'EMERGENCY_REPORT_POLICE', '报警', '紧急情况场景标签', 18020),
('SCENE', 'EMERGENCY', 'EMERGENCY_LOST_ITEM', '失物', '紧急情况场景标签', 18030),
('SCENE', 'EMERGENCY', 'EMERGENCY_ACCIDENT', '事故', '紧急情况场景标签', 18040),
('SCENE', 'EMERGENCY', 'EMERGENCY_FEELING_UNWELL', '身体不适', '紧急情况场景标签', 18050),
('SCENE', 'EMERGENCY', 'EMERGENCY_DISASTER', '灾害', '紧急情况场景标签', 18060),
('SCENE', null, 'GENERAL', '泛用对话', '场景一级标签', 19000),
('SCENE', 'GENERAL', 'GENERAL_NO_SPECIFIC_SCENE', '无明确场景', '泛用对话场景标签', 19010),
('SCENE', 'GENERAL', 'GENERAL_CONVERSATION', '一般交流', '泛用对话场景标签', 19020),
('FUNCTION', null, 'FUNCTION_INFO_EXCHANGE', '信息交流', '功能一级标签', 20000),
('FUNCTION', 'FUNCTION_INFO_EXCHANGE', 'FUNCTION_STATE_FACT', '陈述事实', '信息交流功能标签', 20010),
('FUNCTION', 'FUNCTION_INFO_EXCHANGE', 'FUNCTION_PROVIDE_INFO', '提供信息', '信息交流功能标签', 20020),
('FUNCTION', 'FUNCTION_INFO_EXCHANGE', 'FUNCTION_ASK_INFO', '询问信息', '信息交流功能标签', 20030),
('FUNCTION', 'FUNCTION_INFO_EXCHANGE', 'FUNCTION_ANSWER_QUESTION', '回答问题', '信息交流功能标签', 20040),
('FUNCTION', 'FUNCTION_INFO_EXCHANGE', 'FUNCTION_CONFIRM_INFO', '确认信息', '信息交流功能标签', 20050),
('FUNCTION', 'FUNCTION_INFO_EXCHANGE', 'FUNCTION_ADD_INFO', '补充信息', '信息交流功能标签', 20060),
('FUNCTION', 'FUNCTION_INFO_EXCHANGE', 'FUNCTION_CORRECT_INFO', '纠正信息', '信息交流功能标签', 20070),
('FUNCTION', null, 'FUNCTION_DESCRIPTION', '描述', '功能一级标签', 21000),
('FUNCTION', 'FUNCTION_DESCRIPTION', 'FUNCTION_DESCRIBE_PERSON', '描述人物', '描述功能标签', 21010),
('FUNCTION', 'FUNCTION_DESCRIPTION', 'FUNCTION_DESCRIBE_OBJECT', '描述事物', '描述功能标签', 21020),
('FUNCTION', 'FUNCTION_DESCRIPTION', 'FUNCTION_DESCRIBE_STATE', '描述状态', '描述功能标签', 21030),
('FUNCTION', 'FUNCTION_DESCRIPTION', 'FUNCTION_DESCRIBE_CHANGE', '描述变化', '描述功能标签', 21040),
('FUNCTION', 'FUNCTION_DESCRIPTION', 'FUNCTION_DESCRIBE_EXPERIENCE', '描述经历', '描述功能标签', 21050),
('FUNCTION', 'FUNCTION_DESCRIPTION', 'FUNCTION_DESCRIBE_PROCESS', '描述过程', '描述功能标签', 21060),
('FUNCTION', null, 'FUNCTION_OPINION', '意见', '功能一级标签', 22000),
('FUNCTION', 'FUNCTION_OPINION', 'FUNCTION_EXPRESS_OPINION', '表达意见', '意见功能标签', 22010),
('FUNCTION', 'FUNCTION_OPINION', 'FUNCTION_ASK_OPINION', '询问意见', '意见功能标签', 22020),
('FUNCTION', 'FUNCTION_OPINION', 'FUNCTION_AGREE', '赞同', '意见功能标签', 22030),
('FUNCTION', 'FUNCTION_OPINION', 'FUNCTION_PARTLY_AGREE', '部分赞同', '意见功能标签', 22040),
('FUNCTION', 'FUNCTION_OPINION', 'FUNCTION_DISAGREE', '反对', '意见功能标签', 22050),
('FUNCTION', 'FUNCTION_OPINION', 'FUNCTION_RESERVE_OPINION', '保留意见', '意见功能标签', 22060),
('FUNCTION', null, 'FUNCTION_JUDGMENT', '判断', '功能一级标签', 23000),
('FUNCTION', 'FUNCTION_JUDGMENT', 'FUNCTION_EXPRESS_JUDGMENT', '表达判断', '判断功能标签', 23010),
('FUNCTION', 'FUNCTION_JUDGMENT', 'FUNCTION_INFER', '推测', '判断功能标签', 23020),
('FUNCTION', 'FUNCTION_JUDGMENT', 'FUNCTION_POSSIBILITY', '可能性', '判断功能标签', 23030),
('FUNCTION', 'FUNCTION_JUDGMENT', 'FUNCTION_UNCERTAINTY', '不确定', '判断功能标签', 23040),
('FUNCTION', 'FUNCTION_JUDGMENT', 'FUNCTION_COMPARE', '比较', '判断功能标签', 23050),
('FUNCTION', 'FUNCTION_JUDGMENT', 'FUNCTION_EVALUATE', '评价', '判断功能标签', 23060),
('FUNCTION', null, 'FUNCTION_EMOTION', '感情', '功能一级标签', 24000),
('FUNCTION', 'FUNCTION_EMOTION', 'FUNCTION_EXPRESS_HAPPINESS', '表达开心', '感情功能标签', 24010),
('FUNCTION', 'FUNCTION_EMOTION', 'FUNCTION_EXPRESS_SATISFACTION', '满意', '感情功能标签', 24020),
('FUNCTION', 'FUNCTION_EMOTION', 'FUNCTION_EXPRESS_LIKE', '喜欢', '感情功能标签', 24030),
('FUNCTION', 'FUNCTION_EMOTION', 'FUNCTION_EXPRESS_DISLIKE', '不喜欢', '感情功能标签', 24040),
('FUNCTION', 'FUNCTION_EMOTION', 'FUNCTION_EXPRESS_DISSATISFACTION', '不满', '感情功能标签', 24050),
('FUNCTION', 'FUNCTION_EMOTION', 'FUNCTION_EXPRESS_DISAPPOINTMENT', '失望', '感情功能标签', 24060),
('FUNCTION', 'FUNCTION_EMOTION', 'FUNCTION_EXPRESS_SURPRISE', '惊讶', '感情功能标签', 24070),
('FUNCTION', 'FUNCTION_EMOTION', 'FUNCTION_EXPRESS_WORRY', '担心', '感情功能标签', 24080),
('FUNCTION', 'FUNCTION_EMOTION', 'FUNCTION_EXPRESS_FEAR', '害怕', '感情功能标签', 24090),
('FUNCTION', 'FUNCTION_EMOTION', 'FUNCTION_EXPRESS_REGRET', '遗憾', '感情功能标签', 24100),
('FUNCTION', 'FUNCTION_EMOTION', 'FUNCTION_EXPRESS_CONFUSION', '困惑', '感情功能标签', 24110),
('FUNCTION', null, 'FUNCTION_INTENTION', '意愿', '功能一级标签', 25000),
('FUNCTION', 'FUNCTION_INTENTION', 'FUNCTION_EXPRESS_INTENTION', '表达意愿', '意愿功能标签', 25010),
('FUNCTION', 'FUNCTION_INTENTION', 'FUNCTION_EXPRESS_HOPE', '表达希望', '意愿功能标签', 25020),
('FUNCTION', 'FUNCTION_INTENTION', 'FUNCTION_EXPRESS_PLAN', '表达计划', '意愿功能标签', 25030),
('FUNCTION', 'FUNCTION_INTENTION', 'FUNCTION_EXPRESS_DECISION', '表达决定', '意愿功能标签', 25040),
('FUNCTION', 'FUNCTION_INTENTION', 'FUNCTION_REFUSE_INTENTION', '拒绝意愿', '意愿功能标签', 25050),
('FUNCTION', 'FUNCTION_INTENTION', 'FUNCTION_CHANGE_DECISION', '改变决定', '意愿功能标签', 25060),
('FUNCTION', null, 'FUNCTION_REQUEST', '请求', '功能一级标签', 26000),
('FUNCTION', 'FUNCTION_REQUEST', 'FUNCTION_MAKE_REQUEST', '提出请求', '请求功能标签', 26010),
('FUNCTION', 'FUNCTION_REQUEST', 'FUNCTION_REQUEST_HELP', '请求帮助', '请求功能标签', 26020),
('FUNCTION', 'FUNCTION_REQUEST', 'FUNCTION_REQUEST_CONFIRMATION', '请求确认', '请求功能标签', 26030),
('FUNCTION', 'FUNCTION_REQUEST', 'FUNCTION_REQUEST_EXPLANATION', '请求说明', '请求功能标签', 26040),
('FUNCTION', null, 'FUNCTION_INSTRUCTION', '指示', '功能一级标签', 27000),
('FUNCTION', 'FUNCTION_INSTRUCTION', 'FUNCTION_INSTRUCT', '指示', '指示功能标签', 27010),
('FUNCTION', 'FUNCTION_INSTRUCTION', 'FUNCTION_COMMAND', '命令', '指示功能标签', 27020),
('FUNCTION', 'FUNCTION_INSTRUCTION', 'FUNCTION_PROHIBIT', '禁止', '指示功能标签', 27030),
('FUNCTION', 'FUNCTION_INSTRUCTION', 'FUNCTION_REMIND', '提醒', '指示功能标签', 27040),
('FUNCTION', 'FUNCTION_INSTRUCTION', 'FUNCTION_WARN', '警告', '指示功能标签', 27050),
('FUNCTION', 'FUNCTION_INSTRUCTION', 'FUNCTION_URGE', '催促', '指示功能标签', 27060),
('FUNCTION', null, 'FUNCTION_SUGGESTION', '建议', '功能一级标签', 28000),
('FUNCTION', 'FUNCTION_SUGGESTION', 'FUNCTION_MAKE_SUGGESTION', '提出建议', '建议功能标签', 28010),
('FUNCTION', 'FUNCTION_SUGGESTION', 'FUNCTION_ASK_SUGGESTION', '征求建议', '建议功能标签', 28020),
('FUNCTION', 'FUNCTION_SUGGESTION', 'FUNCTION_RECOMMEND', '推荐', '建议功能标签', 28030),
('FUNCTION', 'FUNCTION_SUGGESTION', 'FUNCTION_ADVISE', '劝告', '建议功能标签', 28040),
('FUNCTION', 'FUNCTION_SUGGESTION', 'FUNCTION_PROPOSE_PLAN', '提出方案', '建议功能标签', 28050),
('FUNCTION', null, 'FUNCTION_INVITATION', '邀请', '功能一级标签', 29000),
('FUNCTION', 'FUNCTION_INVITATION', 'FUNCTION_INVITE', '邀请', '邀请功能标签', 29010),
('FUNCTION', 'FUNCTION_INVITATION', 'FUNCTION_ACCEPT_INVITATION', '接受邀请', '邀请功能标签', 29020),
('FUNCTION', 'FUNCTION_INVITATION', 'FUNCTION_DECLINE_INVITATION', '拒绝邀请', '邀请功能标签', 29030),
('FUNCTION', 'FUNCTION_INVITATION', 'FUNCTION_MAKE_APPOINTMENT', '约定', '邀请功能标签', 29040),
('FUNCTION', 'FUNCTION_INVITATION', 'FUNCTION_ADJUST_APPOINTMENT', '调整约定', '邀请功能标签', 29050),
('FUNCTION', 'FUNCTION_INVITATION', 'FUNCTION_CANCEL_APPOINTMENT', '取消约定', '邀请功能标签', 29060),
('FUNCTION', null, 'FUNCTION_PERMISSION', '许可', '功能一级标签', 30000),
('FUNCTION', 'FUNCTION_PERMISSION', 'FUNCTION_ASK_PERMISSION', '询问许可', '许可功能标签', 30010),
('FUNCTION', 'FUNCTION_PERMISSION', 'FUNCTION_GRANT_PERMISSION', '给予许可', '许可功能标签', 30020),
('FUNCTION', 'FUNCTION_PERMISSION', 'FUNCTION_REFUSE_PERMISSION', '拒绝许可', '许可功能标签', 30030),
('FUNCTION', null, 'FUNCTION_SOCIAL_ETIQUETTE', '社交礼仪', '功能一级标签', 31000),
('FUNCTION', 'FUNCTION_SOCIAL_ETIQUETTE', 'FUNCTION_GREETING', '问候', '社交礼仪功能标签', 31010),
('FUNCTION', 'FUNCTION_SOCIAL_ETIQUETTE', 'FUNCTION_FAREWELL', '告别', '社交礼仪功能标签', 31020),
('FUNCTION', 'FUNCTION_SOCIAL_ETIQUETTE', 'FUNCTION_THANK', '感谢', '社交礼仪功能标签', 31030),
('FUNCTION', 'FUNCTION_SOCIAL_ETIQUETTE', 'FUNCTION_RESPOND_THANKS', '回应感谢', '社交礼仪功能标签', 31040),
('FUNCTION', 'FUNCTION_SOCIAL_ETIQUETTE', 'FUNCTION_APOLOGIZE', '道歉', '社交礼仪功能标签', 31050),
('FUNCTION', 'FUNCTION_SOCIAL_ETIQUETTE', 'FUNCTION_RESPOND_APOLOGY', '回应道歉', '社交礼仪功能标签', 31060),
('FUNCTION', 'FUNCTION_SOCIAL_ETIQUETTE', 'FUNCTION_CONGRATULATE', '祝贺', '社交礼仪功能标签', 31070),
('FUNCTION', 'FUNCTION_SOCIAL_ETIQUETTE', 'FUNCTION_BLESSING', '祝福', '社交礼仪功能标签', 31080),
('FUNCTION', null, 'FUNCTION_INTRODUCTION', '介绍', '功能一级标签', 32000),
('FUNCTION', 'FUNCTION_INTRODUCTION', 'FUNCTION_SELF_INTRODUCTION', '自我介绍', '介绍功能标签', 32010),
('FUNCTION', 'FUNCTION_INTRODUCTION', 'FUNCTION_INTRODUCE_OTHERS', '介绍他人', '介绍功能标签', 32020),
('FUNCTION', 'FUNCTION_INTRODUCTION', 'FUNCTION_INTRODUCE_OBJECT', '介绍事物', '介绍功能标签', 32030),
('FUNCTION', 'FUNCTION_INTRODUCTION', 'FUNCTION_EXPLAIN_IDENTITY', '说明身份', '介绍功能标签', 32040),
('FUNCTION', null, 'FUNCTION_REASON_LOGIC', '原因逻辑', '功能一级标签', 33000),
('FUNCTION', 'FUNCTION_REASON_LOGIC', 'FUNCTION_EXPLAIN_REASON', '说明原因', '原因逻辑功能标签', 33010),
('FUNCTION', 'FUNCTION_REASON_LOGIC', 'FUNCTION_EXPLAIN_RESULT', '说明结果', '原因逻辑功能标签', 33020),
('FUNCTION', 'FUNCTION_REASON_LOGIC', 'FUNCTION_EXPLAIN_PURPOSE', '说明目的', '原因逻辑功能标签', 33030),
('FUNCTION', 'FUNCTION_REASON_LOGIC', 'FUNCTION_EXPLAIN_CONDITION', '说明条件', '原因逻辑功能标签', 33040),
('FUNCTION', 'FUNCTION_REASON_LOGIC', 'FUNCTION_GIVE_EXAMPLE', '举例', '原因逻辑功能标签', 33050),
('FUNCTION', 'FUNCTION_REASON_LOGIC', 'FUNCTION_SUMMARIZE', '总结', '原因逻辑功能标签', 33060),
('FUNCTION', null, 'FUNCTION_TIME_ACTION', '时间行为', '功能一级标签', 34000),
('FUNCTION', 'FUNCTION_TIME_ACTION', 'FUNCTION_EXPRESS_START', '表达开始', '时间行为功能标签', 34010),
('FUNCTION', 'FUNCTION_TIME_ACTION', 'FUNCTION_EXPRESS_END', '结束', '时间行为功能标签', 34030),
('FUNCTION', 'FUNCTION_TIME_ACTION', 'FUNCTION_EXPRESS_COMPLETION', '完成', '时间行为功能标签', 34040),
('FUNCTION', 'FUNCTION_TIME_ACTION', 'FUNCTION_EXPRESS_EXPERIENCE', '经验', '时间行为功能标签', 34050),
('FUNCTION', 'FUNCTION_TIME_ACTION', 'FUNCTION_EXPRESS_HABIT', '习惯', '时间行为功能标签', 34060),
('FUNCTION', 'FUNCTION_TIME_ACTION', 'FUNCTION_EXPRESS_FREQUENCY', '频率', '时间行为功能标签', 34070),
('FUNCTION', null, 'FUNCTION_ABILITY_OBLIGATION', '能力义务', '功能一级标签', 35000),
('FUNCTION', 'FUNCTION_ABILITY_OBLIGATION', 'FUNCTION_EXPRESS_ABILITY', '表达能力', '能力义务功能标签', 35010),
('FUNCTION', 'FUNCTION_ABILITY_OBLIGATION', 'FUNCTION_EXPRESS_POSSIBILITY', '表达可能', '能力义务功能标签', 35020),
('FUNCTION', 'FUNCTION_ABILITY_OBLIGATION', 'FUNCTION_EXPRESS_OBLIGATION', '表达义务', '能力义务功能标签', 35030),
('FUNCTION', 'FUNCTION_ABILITY_OBLIGATION', 'FUNCTION_EXPRESS_NECESSITY', '表达必要', '能力义务功能标签', 35040),
('FUNCTION', 'FUNCTION_ABILITY_OBLIGATION', 'FUNCTION_EXPRESS_UNNECESSARY', '不必要', '能力义务功能标签', 35050),
('FUNCTION', null, 'FUNCTION_CHOICE_DECISION', '选择决策', '功能一级标签', 36000),
('FUNCTION', 'FUNCTION_CHOICE_DECISION', 'FUNCTION_PROVIDE_CHOICE', '提供选择', '选择决策功能标签', 36010),
('FUNCTION', 'FUNCTION_CHOICE_DECISION', 'FUNCTION_ASK_CHOICE', '询问选择', '选择决策功能标签', 36020),
('FUNCTION', 'FUNCTION_CHOICE_DECISION', 'FUNCTION_EXPRESS_PREFERENCE', '表达偏好', '选择决策功能标签', 36030),
('FUNCTION', 'FUNCTION_CHOICE_DECISION', 'FUNCTION_MAKE_CHOICE', '做出选择', '选择决策功能标签', 36040),
('FUNCTION', 'FUNCTION_CHOICE_DECISION', 'FUNCTION_COMPARE_CHOICES', '比较选择', '选择决策功能标签', 36050),
('FUNCTION', null, 'FUNCTION_NEGOTIATION', '交涉', '功能一级标签', 37000),
('FUNCTION', 'FUNCTION_NEGOTIATION', 'FUNCTION_MAKE_DEMAND', '提出要求', '交涉功能标签', 37010),
('FUNCTION', 'FUNCTION_NEGOTIATION', 'FUNCTION_ACCEPT_CONDITION', '接受条件', '交涉功能标签', 37020),
('FUNCTION', 'FUNCTION_NEGOTIATION', 'FUNCTION_REJECT_CONDITION', '拒绝条件', '交涉功能标签', 37030),
('FUNCTION', 'FUNCTION_NEGOTIATION', 'FUNCTION_NEGOTIATE', '协商', '交涉功能标签', 37040),
('FUNCTION', 'FUNCTION_NEGOTIATION', 'FUNCTION_COMPLAIN', '投诉', '交涉功能标签', 37050),
('FUNCTION', 'FUNCTION_NEGOTIATION', 'FUNCTION_RESPOND_COMPLAINT', '回应投诉', '交涉功能标签', 37060),
('FUNCTION', null, 'FUNCTION_CONVERSATION_MANAGEMENT', '会话管理', '功能一级标签', 38000),
('FUNCTION', 'FUNCTION_CONVERSATION_MANAGEMENT', 'FUNCTION_START_TOPIC', '开启话题', '会话管理功能标签', 38010),
('FUNCTION', 'FUNCTION_CONVERSATION_MANAGEMENT', 'FUNCTION_CHANGE_TOPIC', '转换话题', '会话管理功能标签', 38020),
('FUNCTION', 'FUNCTION_CONVERSATION_MANAGEMENT', 'FUNCTION_CONTINUE_TOPIC', '继续话题', '会话管理功能标签', 38030),
('FUNCTION', 'FUNCTION_CONVERSATION_MANAGEMENT', 'FUNCTION_END_TOPIC', '结束话题', '会话管理功能标签', 38040),
('FUNCTION', 'FUNCTION_CONVERSATION_MANAGEMENT', 'FUNCTION_INTERRUPT', '打断', '会话管理功能标签', 38050),
('FUNCTION', 'FUNCTION_CONVERSATION_MANAGEMENT', 'FUNCTION_REQUEST_REPEAT', '请求重复', '会话管理功能标签', 38060),
('FUNCTION', 'FUNCTION_CONVERSATION_MANAGEMENT', 'FUNCTION_CONFIRM_UNDERSTANDING', '确认理解', '会话管理功能标签', 38070),
('FUNCTION', 'FUNCTION_CONVERSATION_MANAGEMENT', 'FUNCTION_SHOW_UNDERSTANDING', '表示理解', '会话管理功能标签', 38080),
('FUNCTION', null, 'FUNCTION_RELATION_INTERACTION', '关系互动', '功能一级标签', 39000),
('FUNCTION', 'FUNCTION_RELATION_INTERACTION', 'FUNCTION_PRAISE', '称赞', '关系互动功能标签', 39010),
('FUNCTION', 'FUNCTION_RELATION_INTERACTION', 'FUNCTION_ENCOURAGE', '鼓励', '关系互动功能标签', 39020),
('FUNCTION', 'FUNCTION_RELATION_INTERACTION', 'FUNCTION_COMFORT', '安慰', '关系互动功能标签', 39030),
('FUNCTION', 'FUNCTION_RELATION_INTERACTION', 'FUNCTION_SHOW_CARE', '关心', '关系互动功能标签', 39040),
('FUNCTION', 'FUNCTION_RELATION_INTERACTION', 'FUNCTION_BLAME', '责备', '关系互动功能标签', 39050),
('FUNCTION', 'FUNCTION_RELATION_INTERACTION', 'FUNCTION_GRUMBLE', '抱怨', '关系互动功能标签', 39060),
('FUNCTION', 'FUNCTION_RELATION_INTERACTION', 'FUNCTION_JOKE', '开玩笑', '关系互动功能标签', 39070),
('FUNCTION', null, 'FUNCTION_INFO_SEEKING', '信息获取', '功能一级标签', 40000),
('FUNCTION', 'FUNCTION_INFO_SEEKING', 'FUNCTION_ASK_PERSON', '询问人物', '信息获取功能标签', 40010),
('FUNCTION', 'FUNCTION_INFO_SEEKING', 'FUNCTION_ASK_PLACE', '地点', '信息获取功能标签', 40020),
('FUNCTION', 'FUNCTION_INFO_SEEKING', 'FUNCTION_ASK_TIME', '时间', '信息获取功能标签', 40030),
('FUNCTION', 'FUNCTION_INFO_SEEKING', 'FUNCTION_ASK_QUANTITY', '数量', '信息获取功能标签', 40040),
('FUNCTION', 'FUNCTION_INFO_SEEKING', 'FUNCTION_ASK_PRICE', '价格', '信息获取功能标签', 40050),
('FUNCTION', 'FUNCTION_INFO_SEEKING', 'FUNCTION_ASK_METHOD', '方法', '信息获取功能标签', 40060),
('FUNCTION', 'FUNCTION_INFO_SEEKING', 'FUNCTION_ASK_REASON', '原因', '信息获取功能标签', 40070),
('FUNCTION', 'FUNCTION_INFO_SEEKING', 'FUNCTION_ASK_STATUS', '状态', '信息获取功能标签', 40080),
('FUNCTION', null, 'FUNCTION_GENERAL', '泛用', '功能一级标签', 41000),
('FUNCTION', 'FUNCTION_GENERAL', 'FUNCTION_OTHER', '其他', '泛用功能标签', 41010);

with localized_seed_tags as (
    select
        seed_tags.*,
        case code
            when 'PART_TIME_JOB' then 'Part-time Work'
            when 'PUBLIC_SERVICE' then 'Public Services'
            when 'DAILY_LIFE_DATE_TIME' then 'Dates & Times'
            when 'WORK_SUPERVISOR_SUBORDINATE' then 'Managers & Team Members'
            when 'WORK_TASK_COMMUNICATION' then 'Task Discussion'
            when 'SHOPPING_RETURN_EXCHANGE' then 'Returns & Exchanges'
            when 'DINING_TAKEOUT_DELIVERY' then 'Takeout & Delivery'
            when 'TRANSPORT_STATION_AIRPORT' then 'Stations & Airports'
            when 'TRAVEL_HOTEL_CHECKIN' then 'Hotel Check-in'
            when 'HEALTHCARE_PHYSICAL_CONDITION' then 'Physical Condition'
            when 'PUBLIC_SERVICE_GOVERNMENT_OFFICE' then 'Government Offices'
            when 'COMMUNICATION_DELIVERY_LOGISTICS' then 'Delivery & Logistics'
            when 'LEISURE_ANIME_FILM' then 'Anime & Film'
            when 'RELATIONSHIP_SENIOR_JUNIOR' then 'Senior–Junior Relationships'
            when 'EMERGENCY_REPORT_POLICE' then 'Calling the Police'
            when 'GENERAL_NO_SPECIFIC_SCENE' then 'No Specific Context'
            when 'FUNCTION_STATE_FACT' then 'Stating Facts'
            when 'FUNCTION_EXPRESS_HABIT' then 'Describing Habits'
            when 'FUNCTION_INFO_EXCHANGE' then 'Information Exchange'
            when 'FUNCTION_REASON_LOGIC' then 'Reasons & Logic'
            when 'FUNCTION_TIME_ACTION' then 'Time & Actions'
            when 'FUNCTION_ABILITY_OBLIGATION' then 'Ability & Obligation'
            when 'FUNCTION_CHOICE_DECISION' then 'Choices & Decisions'
            when 'FUNCTION_RELATION_INTERACTION' then 'Social Interaction'
            when 'FUNCTION_INFO_SEEKING' then 'Finding Information'
            else initcap(replace(
                case
                    when tag_type = 'SCENE' and parent_code is not null
                        then regexp_replace(code, '^' || parent_code || '_', '')
                    else regexp_replace(code, '^(SCENE|FUNCTION)_', '')
                end,
                '_',
                ' '
            ))
        end as name_en
    from seed_tags
)
insert into tags (
    tag_type,
    parent_id,
    code,
    name,
    description,
    name_en,
    description_en,
    sort_order,
    enabled,
    deleted,
    created_at,
    updated_at
)
select
    tag_type,
    null,
    code,
    name,
    description,
    name_en,
    case
        when tag_type = 'SCENE' then 'Practice situations involving ' || lower(name_en) || '.'
        else 'Communicative function: ' || name_en || '.'
    end,
    sort_order,
    true,
    false,
    current_timestamp,
    current_timestamp
from localized_seed_tags
order by sort_order
on conflict (code) do update set
    tag_type = excluded.tag_type,
    parent_id = null,
    name = excluded.name,
    description = excluded.description,
    name_en = excluded.name_en,
    description_en = excluded.description_en,
    sort_order = excluded.sort_order,
    enabled = true,
    deleted = false,
    updated_at = current_timestamp;

update tags child
set
    parent_id = parent.id,
    updated_at = current_timestamp
from seed_tags seed
join tags parent on parent.code = seed.parent_code
where child.code = seed.code
  and seed.parent_code is not null
  and child.parent_id is distinct from parent.id;

drop table seed_tags;

insert into tags (
    tag_type,
    parent_id,
    code,
    name,
    description,
    name_en,
    description_en,
    sort_order,
    enabled,
    deleted,
    created_at,
    updated_at
)
values
    ('GENRE', null, 'NARRATIVE', '叙事文', '按时间或事件发展叙述经历和故事', 'Narrative', 'Narrates experiences or stories in chronological or event order', 30000, true, false, current_timestamp, current_timestamp),
    ('GENRE', null, 'EXPOSITORY', '说明文', '说明事物、方法、现象或知识', 'Expository', 'Explains objects, methods, phenomena, or knowledge', 30010, true, false, current_timestamp, current_timestamp),
    ('GENRE', null, 'OPINION', '观点文', '表达观点并说明理由', 'Opinion', 'Presents an opinion and supporting reasons', 30020, true, false, current_timestamp, current_timestamp),
    ('GENRE', null, 'PRACTICAL', '实用文', '通知、邮件、报告等实际用途文章', 'Practical', 'Practical writing such as notices, emails, and reports', 30030, true, false, current_timestamp, current_timestamp),
    ('GENRE', null, 'ESSAY', '随笔', '围绕见闻、感受和思考自由展开', 'Essay', 'Develops observations, feelings, and reflections freely', 30040, true, false, current_timestamp, current_timestamp),
    ('GENRE', null, 'DIARY', '日记', '记录特定时间的经历、情绪和想法', 'Diary', 'Records experiences, emotions, and thoughts', 30050, true, false, current_timestamp, current_timestamp),
    ('GENRE', null, 'DIALOGUE', '对话', '以人物对话推动信息交流或冲突', 'Dialogue', 'Uses dialogue to exchange information or develop conflict', 30060, true, false, current_timestamp, current_timestamp),
    ('GENRE', null, 'NEWS_REPORT', '新闻报道', '客观报道事件、影响和应对', 'News report', 'Reports events, effects, and responses objectively', 30070, true, false, current_timestamp, current_timestamp),
    ('GENRE', null, 'INTERVIEW', '访谈', '通过问答呈现受访者经历和观点', 'Interview', 'Presents experiences and views through questions and answers', 30080, true, false, current_timestamp, current_timestamp),
    ('GENRE', null, 'REVIEW', '评测', '评价产品、作品、服务或体验', 'Review', 'Evaluates a product, work, service, or experience', 30090, true, false, current_timestamp, current_timestamp),
    ('GENRE', null, 'GUIDE', '指南', '面向目标人群提供步骤和实用建议', 'Guide', 'Provides practical steps and advice', 30100, true, false, current_timestamp, current_timestamp),
    ('GENRE', null, 'FICTION', '小说', '通过虚构人物、环境和冲突展开内容', 'Fiction', 'Develops fictional characters, settings, and conflicts', 30110, true, false, current_timestamp, current_timestamp)
on conflict (code) do update set
    tag_type = excluded.tag_type,
    parent_id = excluded.parent_id,
    name = excluded.name,
    description = excluded.description,
    name_en = excluded.name_en,
    description_en = excluded.description_en,
    sort_order = excluded.sort_order,
    enabled = true,
    deleted = false,
    updated_at = current_timestamp;

-- 错误类型初始化数据

insert into error_types (
    parent_id,
    type_level,
    code,
    name,
    description,
    name_en,
    description_en,
    sort_order,
    enabled,
    deleted,
    created_at,
    updated_at
)
values
    (null, 1, 'SEMANTIC', '语义与信息完整性', '含义传达和信息保留方面的错误', 'Meaning and completeness', 'Errors in meaning transfer and information retention', 1000, true, false, current_timestamp, current_timestamp),
    (null, 1, 'LEXICAL_EXPRESSION', '词汇与表达自然度', '词义、搭配和表达自然度方面的错误', 'Vocabulary and natural expression', 'Errors in word meaning, collocation, and natural expression', 2000, true, false, current_timestamp, current_timestamp),
    (null, 1, 'GRAMMAR_SYNTAX', '语法与句法', '语法结构、助词和动词使用方面的错误', 'Grammar and syntax', 'Errors in grammar, particles, and verb usage', 3000, true, false, current_timestamp, current_timestamp),
    (null, 1, 'PRAGMATICS_CONTEXT', '语用与场景适配', '敬语、语体和场景适配方面的错误', 'Pragmatics and context', 'Errors in register, politeness, and contextual fit', 4000, true, false, current_timestamp, current_timestamp),
    (null, 1, 'WRITING_FORMAT', '书写与格式', '假名、汉字和书写格式方面的错误', 'Writing and format', 'Errors in kana, kanji, orthography, and formatting', 5000, true, false, current_timestamp, current_timestamp)
on conflict (code) do update set
    name = excluded.name,
    description = excluded.description,
    name_en = excluded.name_en,
    description_en = excluded.description_en,
    sort_order = excluded.sort_order,
    enabled = true,
    deleted = false,
    updated_at = current_timestamp;

insert into error_types (
    parent_id,
    type_level,
    code,
    name,
    description,
    name_en,
    description_en,
    sort_order,
    enabled,
    deleted,
    created_at,
    updated_at
)
select
    parent.id,
    2,
    seed.code,
    seed.name,
    seed.description,
    seed.name_en,
    seed.description_en,
    seed.sort_order,
    true,
    false,
    current_timestamp,
    current_timestamp
from (
    values
        ('SEMANTIC', 'OMISSION', '漏译', '遗漏原文中的必要信息', 'Missing information', 'Required information from the source is missing.', 1010),
        ('SEMANTIC', 'MISTRANSLATION', '误译', '对原文含义的理解或翻译错误', 'Incorrect meaning', 'The source meaning was misunderstood or translated incorrectly.', 1020),
        ('SEMANTIC', 'ADDITION', '过度发挥', '加入原文没有的信息', 'Unsupported addition', 'The answer adds information that is not present in the source.', 1030),
        ('SEMANTIC', 'SUBJECT_OBJECT', '主语或对象错误', '人物、对象或动作归属错误', 'Wrong subject or object', 'A person, object, or action is assigned to the wrong participant.', 1040),
        ('SEMANTIC', 'LOGIC_RELATION', '逻辑关系错误', '因果、转折、条件等逻辑关系错误', 'Incorrect logical relationship', 'A causal, contrasting, conditional, or other logical relationship is incorrect.', 1050),
        ('LEXICAL_EXPRESSION', 'WORD_SENSE', '词义混淆', '未按语境选择正确词义', 'Wrong word sense', 'The selected meaning of a word does not fit the context.', 2010),
        ('LEXICAL_EXPRESSION', 'SYNONYM', '近义词误用', '近义词的语义范围或使用条件不符', 'Inappropriate synonym', 'A synonym does not fit the intended nuance or usage conditions.', 2020),
        ('LEXICAL_EXPRESSION', 'COLLOCATION', '搭配错误', '词语组合不符合日语常用搭配', 'Unnatural collocation', 'The word combination is not idiomatic in Japanese.', 2030),
        ('LEXICAL_EXPRESSION', 'FALSE_FRIEND', '中日同形词误用', '误按中文含义使用日语同形词', 'False friend', 'A similar-looking Chinese and Japanese word is used with the wrong meaning.', 2040),
        ('LEXICAL_EXPRESSION', 'CHINESE_CALQUE', '中文直译', '直接套用中文表达导致不自然', 'Chinese calque', 'Chinese phrasing is copied too literally, resulting in unnatural Japanese.', 2050),
        ('LEXICAL_EXPRESSION', 'WORD_ORDER', '语序生硬', '语序可理解但不符合自然日语习惯', 'Unnatural word order', 'The meaning is understandable, but the word order is not natural in Japanese.', 2060),
        ('LEXICAL_EXPRESSION', 'REDUNDANCY', '冗余表达', '存在不必要的重复或累赘表达', 'Redundant wording', 'The answer contains unnecessary repetition or wording.', 2070),
        ('LEXICAL_EXPRESSION', 'UNNATURAL_EXPRESSION', '不自然表达', '语义正确但整体表达不地道', 'Unnatural expression', 'The meaning is correct, but the overall expression is not idiomatic.', 2080),
        ('GRAMMAR_SYNTAX', 'SENTENCE_PATTERN', '句型错误', '句型选择或结构不正确', 'Incorrect sentence pattern', 'The sentence pattern or structure is incorrect.', 3010),
        ('GRAMMAR_SYNTAX', 'CONJUGATION', '活用错误', '动词、形容词或助动词活用错误', 'Incorrect conjugation', 'A verb, adjective, or auxiliary is conjugated incorrectly.', 3020),
        ('GRAMMAR_SYNTAX', 'CONNECTION', '接续错误', '词语或句子之间的接续形式错误', 'Incorrect linking form', 'Words or clauses are connected with an incorrect form.', 3030),
        ('GRAMMAR_SYNTAX', 'NEGATION', '否定表达错误', '否定形式或否定范围使用错误', 'Incorrect negation', 'The negative form or scope of negation is incorrect.', 3040),
        ('GRAMMAR_SYNTAX', 'CONDITION', '条件表达错误', '条件句型或条件关系使用错误', 'Incorrect conditional', 'A conditional pattern or relationship is used incorrectly.', 3050),
        ('GRAMMAR_SYNTAX', 'PARTICLE', '助词错误', '助词的选择、位置或语义功能错误', 'Incorrect particle', 'A particle is incorrect in choice, placement, or function.', 3060),
        ('GRAMMAR_SYNTAX', 'TENSE_ASPECT', '时态体貌错误', '时态、持续、完成或状态表达错误', 'Incorrect tense or aspect', 'Tense, duration, completion, or state is expressed incorrectly.', 3070),
        ('GRAMMAR_SYNTAX', 'TRANSITIVE_INTRANSITIVE', '自他动词错误', '自动词和他动词的选择或格助词搭配错误', 'Transitivity error', 'A transitive or intransitive verb, or its particle pattern, is used incorrectly.', 3080),
        ('GRAMMAR_SYNTAX', 'GIVING_RECEIVING', '授受关系错误', '授受动词或受益关系表达错误', 'Giving and receiving', 'A giving or receiving verb, or the beneficiary relationship, is incorrect.', 3090),
        ('PRAGMATICS_CONTEXT', 'POLITENESS', '礼貌程度错误', '礼貌程度与交际关系不匹配', 'Inappropriate politeness', 'The level of politeness does not match the relationship or situation.', 4010),
        ('PRAGMATICS_CONTEXT', 'HONORIFIC', '尊敬语错误', '对对方或第三方的尊敬语使用错误', 'Incorrect respectful language', 'Respectful language for the listener or a third party is used incorrectly.', 4020),
        ('PRAGMATICS_CONTEXT', 'HUMBLE', '谦让语错误', '对己方行为的谦让语使用错误', 'Incorrect humble language', 'Humble language for the speaker or in-group is used incorrectly.', 4030),
        ('PRAGMATICS_CONTEXT', 'STYLE_CONSISTENCY', '语体不一致', '普通体、敬体或书面语体前后混用', 'Inconsistent register', 'Plain, polite, or written styles are mixed inconsistently.', 4040),
        ('PRAGMATICS_CONTEXT', 'FORMALITY', '正式程度错误', '表达正式程度与场景不匹配', 'Inappropriate formality', 'The level of formality does not fit the situation.', 4050),
        ('PRAGMATICS_CONTEXT', 'ADDRESSEE', '对象身份不匹配', '未根据说话对象选择合适表达', 'Addressee mismatch', 'The expression is not appropriate for the person being addressed.', 4060),
        ('WRITING_FORMAT', 'KANA', '假名错误', '平假名、片假名或假名拼写错误', 'Kana error', 'Hiragana, katakana, or kana spelling is incorrect.', 5010),
        ('WRITING_FORMAT', 'KANJI', '汉字错误', '汉字选择、写法或读写对应错误', 'Kanji error', 'The kanji choice, written form, or reading is incorrect.', 5020),
        ('WRITING_FORMAT', 'ORTHOGRAPHY', '表记错误', '长音、促音、送假名等表记错误', 'Orthographic error', 'A long vowel, small tsu, okurigana, or other written form is incorrect.', 5030),
        ('WRITING_FORMAT', 'PUNCTUATION', '标点错误', '标点符号或断句方式不恰当', 'Punctuation error', 'Punctuation or sentence breaks are inappropriate.', 5040),
        ('WRITING_FORMAT', 'INCOMPLETE_INPUT', '输入残缺', '答案存在缺字、截断或未完成输入', 'Incomplete input', 'The answer is missing characters, truncated, or unfinished.', 5050)
) as seed(parent_code, code, name, description, name_en, description_en, sort_order)
inner join error_types parent
    on parent.code = seed.parent_code
   and parent.type_level = 1
   and parent.deleted = false
on conflict (code) do update set
    parent_id = excluded.parent_id,
    name = excluded.name,
    description = excluded.description,
    name_en = excluded.name_en,
    description_en = excluded.description_en,
    sort_order = excluded.sort_order,
    enabled = true,
    deleted = false,
    updated_at = current_timestamp;
