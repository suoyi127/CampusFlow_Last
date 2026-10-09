INSERT INTO study_space (name,type,address,latitude,longitude,capacity,open_time,close_time,open_days,all_day,facilities,description,enabled) VALUES
('图书馆一层自习区','LIBRARY','图书馆东门一层',31.2304,121.4737,80,'07:00','22:00','1,2,3,4,5,6,7',FALSE,'AC,SEAT,POWER,WIFI','安静自习，靠窗座位较多。',TRUE),
('图书馆二层阅览区','LIBRARY','图书馆二层',31.2310,121.4738,60,'07:00','22:00','1,2,3,4,5,6,7',FALSE,'AC,SEAT,WIFI','阅读区域，请保持安静。',TRUE),
('教学楼 A101','CLASSROOM','教学楼 A 座一层',31.2320,121.4750,50,'08:00','21:00','1,2,3,4,5',FALSE,'AC,SEAT,POWER','课余开放教室。',TRUE),
('教学楼 B201','CLASSROOM','教学楼 B 座二层',31.2330,121.4760,40,'08:00','21:00','1,2,3,4,5,6,7',FALSE,'SEAT,POWER,WIFI','适合个人自习。',TRUE),
('学生中心研讨室','DISCUSSION','学生中心三层',31.2290,121.4780,20,'09:00','20:00','1,2,3,4,5,6,7',FALSE,'AC,SEAT,POWER,WIFI','适合小组讨论，噪声可能较高。',TRUE),
('湖畔学习亭','OUTDOOR','校园湖畔北侧',31.2270,121.4710,16,'00:00','23:59','1,2,3,4,5,6,7',TRUE,'SEAT','室外空间，注意天气。',TRUE),
('创新楼共享区','DISCUSSION','创新楼一层',31.2350,121.4790,32,'08:00','23:00','1,2,3,4,5,6,7',FALSE,'AC,SEAT,POWER,WIFI','共享学习与讨论。',TRUE),
('宿舍公共自习室','STUDY_ROOM','宿舍园区服务楼',31.2250,121.4800,36,'00:00','23:59','1,2,3,4,5,6,7',TRUE,'AC,SEAT,POWER,WIFI','全天开放自习室。',TRUE),
('咖啡厅学习角','CAFE','校园咖啡厅',31.2280,121.4690,24,'09:00','19:00','1,2,3,4,5,6,7',FALSE,'AC,SEAT,WIFI','有背景音乐。',TRUE),
('旧楼晚间自习室','STUDY_ROOM','旧教学楼二层',31.2370,121.4820,28,'18:00','23:00','1,2,3,4,5',FALSE,'SEAT,POWER','仅工作日晚间开放。',TRUE);

INSERT INTO noise_device (space_id,device_code,status,base_db,fluctuation_db)
 SELECT id, CONCAT('SIM-NOISE-', id), 'ONLINE', 36 + id * 3, 5 FROM study_space;

INSERT INTO system_config (config_key,config_value,updated_at) VALUES
 ('simulationPeriodSeconds','5',CURRENT_TIMESTAMP),
 ('pollPeriodSeconds','5',CURRENT_TIMESTAMP),
 ('validitySeconds','30',CURRENT_TIMESTAMP),
 ('noiseWindowSeconds','60',CURRENT_TIMESTAMP),
 ('rankingWeights','0.30,0.30,0.25,0.15',CURRENT_TIMESTAMP);
