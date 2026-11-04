ALTER TABLE study_space ADD COLUMN data_origin VARCHAR(24) NOT NULL DEFAULT 'REAL';
ALTER TABLE sys_user ADD COLUMN data_origin VARCHAR(24) NOT NULL DEFAULT 'REAL';
ALTER TABLE hardware_record ADD COLUMN people_valid BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE hardware_record ADD COLUMN people_invalid_reason VARCHAR(500);
ALTER TABLE hardware_record ADD COLUMN noise_valid BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE hardware_record ADD COLUMN noise_invalid_reason VARCHAR(500);

-- 仅隔离与历史种子全部字段一致且没有真实设备关系的空间；不删除业务记录。
UPDATE study_space SET data_origin='LEGACY_SAMPLE' WHERE (
(name='图书馆一层自习区' AND type='LIBRARY' AND address='图书馆东门一层' AND latitude=31.2304 AND longitude=121.4737 AND capacity=80 AND open_time='07:00' AND close_time='22:00' AND open_days='1,2,3,4,5,6,7' AND all_day=FALSE AND facilities='AC,SEAT,POWER,WIFI' AND description='安静自习，靠窗座位较多。' AND enabled=TRUE)
 OR (name='图书馆二层阅览区' AND type='LIBRARY' AND address='图书馆二层' AND latitude=31.2310 AND longitude=121.4738 AND capacity=60 AND open_time='07:00' AND close_time='22:00' AND open_days='1,2,3,4,5,6,7' AND all_day=FALSE AND facilities='AC,SEAT,WIFI' AND description='阅读区域，请保持安静。' AND enabled=TRUE)
 OR (name='教学楼 A101' AND type='CLASSROOM' AND address='教学楼 A 座一层' AND latitude=31.2320 AND longitude=121.4750 AND capacity=50 AND open_time='08:00' AND close_time='21:00' AND open_days='1,2,3,4,5' AND all_day=FALSE AND facilities='AC,SEAT,POWER' AND description='课余开放教室。' AND enabled=TRUE)
 OR (name='教学楼 B201' AND type='CLASSROOM' AND address='教学楼 B 座二层' AND latitude=31.2330 AND longitude=121.4760 AND capacity=40 AND open_time='08:00' AND close_time='21:00' AND open_days='1,2,3,4,5,6,7' AND all_day=FALSE AND facilities='SEAT,POWER,WIFI' AND description='适合个人自习。' AND enabled=TRUE)
 OR (name='学生中心研讨室' AND type='DISCUSSION' AND address='学生中心三层' AND latitude=31.2290 AND longitude=121.4780 AND capacity=20 AND open_time='09:00' AND close_time='20:00' AND open_days='1,2,3,4,5,6,7' AND all_day=FALSE AND facilities='AC,SEAT,POWER,WIFI' AND description='适合小组讨论，噪声可能较高。' AND enabled=TRUE)
 OR (name='湖畔学习亭' AND type='OUTDOOR' AND address='校园湖畔北侧' AND latitude=31.2270 AND longitude=121.4710 AND capacity=16 AND open_time='00:00' AND close_time='23:59' AND open_days='1,2,3,4,5,6,7' AND all_day=TRUE AND facilities='SEAT' AND description='室外空间，注意天气。' AND enabled=TRUE)
 OR (name='创新楼共享区' AND type='DISCUSSION' AND address='创新楼一层' AND latitude=31.2350 AND longitude=121.4790 AND capacity=32 AND open_time='08:00' AND close_time='23:00' AND open_days='1,2,3,4,5,6,7' AND all_day=FALSE AND facilities='AC,SEAT,POWER,WIFI' AND description='共享学习与讨论。' AND enabled=TRUE)
 OR (name='宿舍公共自习室' AND type='STUDY_ROOM' AND address='宿舍园区服务楼' AND latitude=31.2250 AND longitude=121.4800 AND capacity=36 AND open_time='00:00' AND close_time='23:59' AND open_days='1,2,3,4,5,6,7' AND all_day=TRUE AND facilities='AC,SEAT,POWER,WIFI' AND description='全天开放自习室。' AND enabled=TRUE)
 OR (name='咖啡厅学习角' AND type='CAFE' AND address='校园咖啡厅' AND latitude=31.2280 AND longitude=121.4690 AND capacity=24 AND open_time='09:00' AND close_time='19:00' AND open_days='1,2,3,4,5,6,7' AND all_day=FALSE AND facilities='AC,SEAT,WIFI' AND description='有背景音乐。' AND enabled=TRUE)
 OR (name='旧楼晚间自习室' AND type='STUDY_ROOM' AND address='旧教学楼二层' AND latitude=31.2370 AND longitude=121.4820 AND capacity=28 AND open_time='18:00' AND close_time='23:00' AND open_days='1,2,3,4,5' AND all_day=FALSE AND facilities='SEAT,POWER' AND description='仅工作日晚间开放。' AND enabled=TRUE)
) AND NOT EXISTS(SELECT 1 FROM hardware_device d WHERE d.space_id=study_space.id)
  AND NOT EXISTS(SELECT 1 FROM hardware_record r WHERE r.space_id=study_space.id);
