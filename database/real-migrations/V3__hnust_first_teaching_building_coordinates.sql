-- 高德地点联想中“湖南科技大学第一教学楼”（湘潭市雨湖区桃园路）的楼栋点，GCJ-02。
UPDATE study_space
SET latitude = 27.895403,
    longitude = 112.923703,
    coordinate_system = 'GCJ02',
    description = REPLACE(description, '开放时间和地图位置待核实。', '开放时间待核实。')
WHERE data_origin = 'REAL'
  AND type = 'CLASSROOM'
  AND coordinate_system = 'UNKNOWN'
  AND name REGEXP '^湖南科技大学第一教学楼[1-6]0[1-8]教室$';
