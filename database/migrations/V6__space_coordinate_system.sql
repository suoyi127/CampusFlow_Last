ALTER TABLE study_space ADD COLUMN coordinate_system VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';

-- 仅识别未修改的合成演示坐标；其他旧数据必须由管理员确认。
UPDATE study_space SET coordinate_system='GCJ02' WHERE
(name='图书馆一层自习区' AND latitude=31.2304 AND longitude=121.4737)
 OR (name='图书馆二层阅览区' AND latitude=31.2310 AND longitude=121.4738)
 OR (name='教学楼 A101' AND latitude=31.2320 AND longitude=121.4750)
 OR (name='教学楼 B201' AND latitude=31.2330 AND longitude=121.4760)
 OR (name='学生中心研讨室' AND latitude=31.2290 AND longitude=121.4780)
 OR (name='湖畔学习亭' AND latitude=31.2270 AND longitude=121.4710)
 OR (name='创新楼共享区' AND latitude=31.2350 AND longitude=121.4790)
 OR (name='宿舍公共自习室' AND latitude=31.2250 AND longitude=121.4800)
 OR (name='咖啡厅学习角' AND latitude=31.2280 AND longitude=121.4690)
 OR (name='旧楼晚间自习室' AND latitude=31.2370 AND longitude=121.4820);
