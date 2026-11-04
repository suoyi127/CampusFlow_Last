-- 按楼层和房号生成用户提供的一教教室档案。坐标和开放时段待核实，暂不对用户开放。
INSERT INTO study_space (
    name, type, address, latitude, longitude, capacity,
    open_time, close_time, open_days, all_day, facilities, description,
    enabled, coordinate_system, data_origin
)
SELECT
    CONCAT('湖南科技大学第一教学楼', floors.floor_no * 100 + rooms.room_no, '教室'),
    'CLASSROOM',
    CONCAT('湖南科技大学南校区第一教学楼', floors.floor_no, '楼'),
    0, 0, rooms.capacity,
    '00:00', '23:59', '', FALSE, 'SEAT',
    CONCAT(rooms.room_size, '教室。开放时间和地图位置待核实。'),
    FALSE, 'UNKNOWN', 'REAL'
FROM (
    SELECT 1 AS floor_no UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
) floors
CROSS JOIN (
    SELECT 1 AS room_no, 70 AS capacity, '小' AS room_size
    UNION ALL SELECT 2, 150, '中'
    UNION ALL SELECT 3, 150, '中'
    UNION ALL SELECT 4, 300, '大'
    UNION ALL SELECT 5, 150, '中'
    UNION ALL SELECT 6, 150, '中'
    UNION ALL SELECT 7, 150, '中'
    UNION ALL SELECT 8, 150, '中'
) rooms
WHERE NOT EXISTS (
    SELECT 1 FROM study_space existing
    WHERE existing.name = CONCAT('湖南科技大学第一教学楼', floors.floor_no * 100 + rooms.room_no, '教室')
      AND existing.data_origin = 'REAL'
)
ORDER BY floors.floor_no, rooms.room_no;
