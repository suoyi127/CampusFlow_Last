package com.campusflow.hardware;

import com.campusflow.common.BusinessException;
import com.campusflow.simulation.SimulationService;
import com.campusflow.space.SpaceMapper;
import com.campusflow.status.StatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.concurrent.*;
import static com.campusflow.hardware.HardwarePayload.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:hardware_reception;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","campusflow.checkin.device-bindings=AREA_A_001=6","campusflow.checkin.http-token=test-device-token"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(HardwareReceptionTest.TimeConfig.class)
class HardwareReceptionTest {
    static class MutableClock extends Clock {
        volatile Instant now=Instant.parse("2026-10-09T02:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    @TestConfiguration static class TimeConfig { @Bean @Primary MutableClock hardwareTestClock(){return new MutableClock();} }
    @Autowired MutableClock clock;
    @Autowired HardwareService service;
    @Autowired StatusService status;
    @Autowired SpaceMapper spaces;
    @Autowired SimulationService simulation;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired HardwareDeviceService devices;
    @Autowired HardwareDiscovery discovery;
    long now(){return clock.instant().getEpochSecond();}
    @Autowired com.campusflow.space.SpaceService spaceService;
    private void saveSpace(int capacity,boolean enabled) {
        var s=spaces.selectById(6);
        spaceService.save(6L,new com.campusflow.space.SpaceInput(s.name,s.type,s.address,s.latitude,s.longitude,capacity,s.openTime,s.closeTime,
            java.util.Arrays.stream(s.openDays.split(",")).map(Integer::valueOf).toList(),s.allDay,
            java.util.Arrays.asList(s.facilities.split(",")),s.description,enabled,"GCJ02"),"data_admin");
    }
    @Test void invalidHardwareAllowsManagementButNotCapacityReduction() {
        service.telemetry(new Telemetry("AREA_A_001",now(),4294967295L,null,false));
        assertDoesNotThrow(()->saveSpace(16,false));
        assertDoesNotThrow(()->saveSpace(20,true));
        assertEquals("PEOPLE_UNCONFIRMED",assertThrows(BusinessException.class,()->saveSpace(19,true)).code);
        assertEquals(4294967295L,service.currentPeople(6));
    }
    @Test void capacityReductionRequiresFreshValidHardwareCount() {
        assertEquals("PEOPLE_UNCONFIRMED",assertThrows(BusinessException.class,()->saveSpace(15,true)).code);
        service.telemetry(new Telemetry("AREA_A_001",now(),10L,null,false));
        assertEquals("CAPACITY_TOO_SMALL",assertThrows(BusinessException.class,()->saveSpace(9,true)).code);
        assertDoesNotThrow(()->saveSpace(10,true));
        clock.now=clock.now.plusSeconds(31);
        assertEquals("PEOPLE_UNCONFIRMED",assertThrows(BusinessException.class,()->saveSpace(9,true)).code);
        assertDoesNotThrow(()->saveSpace(10,false));
    }
    Event event(String id,EventType type,long count,long at){return new Event("AREA_A_001",id,at,type,count);}
    @BeforeEach void prepare(){
        clock.now=Instant.parse("2026-10-09T02:00:00Z");
        jdbc.update("DELETE FROM hardware_record");jdbc.update("DELETE FROM hardware_rejection");
        jdbc.update("DELETE FROM hardware_discovery");
        jdbc.update("DELETE FROM hardware_device WHERE device_id<>'AREA_A_001'");
        jdbc.update("UPDATE hardware_device SET space_id=6,enabled=TRUE,version=1 WHERE device_id='AREA_A_001'");
        jdbc.update("UPDATE study_space SET capacity=16,enabled=TRUE,all_day=TRUE WHERE id=6");
    }
    @Test void validMqttUploadDiscoversUnknownDeviceAndRegistrationRemovesCandidate() throws Exception {
        var receiver=new HardwareMqttReceiver(service,json,"tcp://localhost:1883","discovery-test","","");
        try {
            var input=new Telemetry("AUTO_BOARD",now(),0L,null,false);
            var body=json.writeValueAsString(input);
            assertThrows(BusinessException.class,()->receiver.receive("checkin/WRONG/telemetry",body,false));
            assertThrows(BusinessException.class,()->receiver.receive("checkin/AUTO_BOARD/telemetry",body,true));
            assertThrows(BusinessException.class,()->service.telemetry(new Telemetry("FUTURE_BOARD",now()+61,0L,null,false)));
            assertTrue(discovery.candidates().isEmpty());
            assertEquals("DEVICE_NOT_BOUND",assertThrows(BusinessException.class,()->receiver.receive("checkin/AUTO_BOARD/telemetry",body,false)).code);
            assertEquals("AUTO_BOARD",discovery.candidates().getFirst().get("deviceId"));
            assertEquals(true,discovery.candidates().getFirst().get("online"));
            assertEquals(0L,service.records(null,null,null,1,20).get("total"));
            clock.now=clock.now.plusSeconds(31);
            assertEquals(false,discovery.candidates().getFirst().get("online"));
            assertThrows(BusinessException.class,()->service.telemetry(new Telemetry("AUTO_BOARD",now(),0L,null,false)));
            assertEquals(1,discovery.candidates().size());
            assertEquals(true,discovery.candidates().getFirst().get("online"));
            devices.save(null,new HardwareDeviceInput("AUTO_BOARD","自动发现设备",5L,true,null),"data_admin");
            assertTrue(discovery.candidates().isEmpty());
            assertEquals(5L,service.telemetry(new Telemetry("AUTO_BOARD",now(),0L,null,false)).spaceId());
        } finally { receiver.stop(); }
    }
    @Test void firstUploadUsesFrontendRegistrationAndBindingChangesAreProtected() {
        var fresh=new Telemetry("NEW_BOARD",now(),2L,50.0,true);
        assertEquals("DEVICE_NOT_BOUND",assertThrows(BusinessException.class,()->service.telemetry(fresh)).code);
        devices.save(null,new HardwareDeviceInput("NEW_BOARD","新设备",null,true,null),"data_admin");
        var row=devices.list().stream().filter(d->d.get("deviceId").equals("NEW_BOARD")).findFirst().orElseThrow();
        long id=((Number)row.get("id")).longValue();
        assertNull(row.get("lastReceivedAt"));
        devices.save(id,new HardwareDeviceInput("NEW_BOARD","新设备",5L,true,1L),"data_admin");
        assertEquals(5L,service.telemetry(fresh).spaceId());
        assertEquals("HARDWARE",status.current(spaces.selectById(5)).source());
        assertEquals("VERSION_CONFLICT",assertThrows(BusinessException.class,()->devices.save(id,new HardwareDeviceInput("NEW_BOARD","新设备",5L,false,1L),"data_admin")).code);
        assertEquals("DEVICE_HAS_HISTORY",assertThrows(BusinessException.class,()->devices.save(id,new HardwareDeviceInput("NEW_BOARD","新设备",4L,true,2L),"data_admin")).code);
        devices.save(id,new HardwareDeviceInput("NEW_BOARD","新设备",5L,false,2L),"data_admin");
        assertEquals("DEVICE_DISABLED",assertThrows(BusinessException.class,()->service.telemetry(fresh)).code);
        assertEquals(1L,service.records(5L,"NEW_BOARD",null,1,20).get("total"));
        assertEquals("SPACE_ALREADY_BOUND",assertThrows(BusinessException.class,()->devices.save(null,new HardwareDeviceInput("OTHER_BOARD","其他设备",5L,true,null),"data_admin")).code);
        assertEquals("DEVICE_EXISTS",assertThrows(BusinessException.class,()->devices.save(null,new HardwareDeviceInput("NEW_BOARD","重名设备",null,true,null),"data_admin")).code);
        assertEquals("SPACE_NOT_FOUND",assertThrows(BusinessException.class,()->devices.save(null,new HardwareDeviceInput("OTHER_BOARD","其他设备",99999L,true,null),"data_admin")).code);
    }
    @Test void deviceManagementRequiresDataAdminSessionAndCsrf() throws Exception {
        mvc.perform(get("/api/data/hardware-devices")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/data/hardware-devices/candidates")).andExpect(status().isUnauthorized());
        var student=(org.springframework.mock.web.MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("username","student").param("password","Demo@123456")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/data/hardware-devices").session(student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/data/hardware-devices/candidates").session(student)).andExpect(status().isForbidden());
        var admin=(org.springframework.mock.web.MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("username","data_admin").param("password","Demo@123456")).andReturn().getRequest().getSession(false);
        var body=json.writeValueAsString(new HardwareDeviceInput("NEW_BOARD","新设备",5L,true,null));
        mvc.perform(post("/api/data/hardware-devices").session(admin).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/data/hardware-devices").session(admin).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$[1].deviceId").value("NEW_BOARD"));
        mvc.perform(get("/api/data/hardware-devices").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/data/hardware-devices/candidates").session(admin)).andExpect(status().isOk());
    }
    @Test void eventsAreIdempotentAndSignOutAtZeroIsAccepted() throws Exception {
        var arrival=event("1",EventType.CHECK_IN,1,now());
        var first=service.event(arrival);
        var replay=service.event(arrival);
        assertFalse(first.duplicate());assertTrue(replay.duplicate());assertEquals(first.recordId(),replay.recordId());
        var conflict=assertThrows(BusinessException.class,()->service.event(event("1",EventType.CHECK_OUT,0,now())));
        assertEquals("EVENT_CONFLICT",conflict.code);
        service.event(event("2",EventType.CHECK_OUT,0,now()));service.event(event("3",EventType.CHECK_OUT,0,now()));
        assertEquals(0,status.current(spaces.selectById(6)).currentPeople());
        assertEquals(3,service.records(6L,null,Kind.EVENT,1,20).get("total") instanceof Long count ? count.intValue():-1);
        // 同时重投共用空间行锁，数据库最终只有一条事件。
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->service.event(event("4",EventType.CHECK_IN,1,now())));
            var b=pool.submit(()->service.event(event("4",EventType.CHECK_IN,1,now())));
            assertNotEquals(a.get(5,TimeUnit.SECONDS).duplicate(),b.get(5,TimeUnit.SECONDS).duplicate());
        }
    }
    @Test void machineTokenIsRequiredWithoutWeakeningSessionCsrf() throws Exception {
        String body=json.writeValueAsString(event("1",EventType.CHECK_IN,1,now()));
        mvc.perform(post("/api/hardware/events").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/hardware/events").header("Authorization","Bearer wrong").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/hardware/events").header("Authorization","Bearer test-device-token").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("duplicate").value(false));
        mvc.perform(post("/api/auth/login").param("username","student").param("password","Demo@123456")).andExpect(status().isForbidden());
        mvc.perform(get("/api/data/hardware-records")).andExpect(status().isUnauthorized());
        var student=mvc.perform(post("/api/auth/login").with(csrf()).param("username","student").param("password","Demo@123456")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/data/hardware-records").session((org.springframework.mock.web.MockHttpSession)student)).andExpect(status().isForbidden());
        var admin=mvc.perform(post("/api/auth/login").with(csrf()).param("username","data_admin").param("password","Demo@123456")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/data/hardware-records?kind=EVENT&spaceId=6").session((org.springframework.mock.web.MockHttpSession)admin)).andExpect(status().isOk()).andExpect(jsonPath("items[0].eventType").value("CHECK_IN"));
    }
    @Test void oldBackfillDoesNotReplaceNewTelemetryAndSimulationResetPreservesHardware() {
        service.telemetry(new Telemetry("AREA_A_001",now(),4L,65.4,true));
        service.event(event("1",EventType.CHECK_IN,1,now()-120));
        simulation.tick();simulation.reset("test");
        var current=status.current(spaces.selectById(6));
        assertEquals("HARDWARE",current.source());assertEquals(4,current.currentPeople());assertEquals(65.4,current.noiseDb());
        assertEquals(2L,service.records(6L,null,null,1,20).get("total"));
        assertTrue(service.event(event("1",EventType.CHECK_IN,1,now()-120)).duplicate());
        clock.now=clock.now.plusSeconds(31);
        assertEquals("EXPIRED",status.current(spaces.selectById(6)).peopleState());
        assertEquals("OFFLINE",status.current(spaces.selectById(6)).deviceState());
    }
    @Test void invalidSensorAndOverCapacityRemainInspectableWithoutFakeValues() {
        service.telemetry(new Telemetry("AREA_A_001",now()-1,3L,50.0,true));
        service.telemetry(new Telemetry("AREA_A_001",now(),20L,null,false));
        var current=status.current(spaces.selectById(6));
        assertEquals("INVALID",current.peopleState());assertNull(current.currentPeople());
        assertEquals("INVALID",current.noiseState());assertNull(current.noiseDb());assertNull(current.typicalNoiseDb());
        assertEquals(2L,service.records(6L,null,null,1,20).get("total"));
    }
    @Test void malformedUploadsAreRejectedAndDeviceTimestampsPreserved() throws Exception {
        assertThrows(BusinessException.class,()->service.event(event("1",EventType.CHECK_IN,1,now()+61)));
        assertThrows(BusinessException.class,()->service.event(event("18446744073709551616",EventType.CHECK_IN,1,now())));
        assertThrows(BusinessException.class,()->service.telemetry(new Telemetry("AREA_A_001",now(),-1L,65.0,true)));
        assertThrows(BusinessException.class,()->service.telemetry(new Telemetry("AREA_A_001",now(),1L,null,true)));
        assertThrows(BusinessException.class,()->service.telemetry(new Telemetry("UNKNOWN",now(),1L,65.0,true)));
        mvc.perform(post("/api/hardware/telemetry").header("Authorization","Bearer test-device-token").contentType("application/json")
            .content("{\"device_id\":\"AREA_A_001\",\"timestamp\":"+now()+",\"headcount\":1.5,\"decibel\":65,\"sound_valid\":true}")).andExpect(status().isBadRequest());
        service.event(event("1",EventType.CHECK_IN,1,now()-100));
        assertEquals(LocalDateTime.ofInstant(clock.instant().minusSeconds(100),ZoneOffset.UTC),jdbc.queryForObject("SELECT sampled_at FROM hardware_record",LocalDateTime.class));
    }
    @Test void mqttDecoderMatchesFirmwareAndRejectsTopicSpoofing() throws Exception {
        var receiver=new HardwareMqttReceiver(service,json,"tcp://localhost:1883","decoder-test","","");
        try {
            receiver.receive("checkin/AREA_A_001/events",json.writeValueAsString(event("1",EventType.CHECK_IN,1,now())),false);
            receiver.receive("checkin/AREA_A_001/telemetry",json.writeValueAsString(new Telemetry("AREA_A_001",now(),1L,65.4,true)),false);
            assertEquals(2L,service.records(6L,null,null,1,20).get("total"));
            assertThrows(BusinessException.class,()->receiver.receive("checkin/OTHER/events",json.writeValueAsString(event("2",EventType.CHECK_IN,2,now())),false));
            assertThrows(BusinessException.class,()->receiver.receive("checkin/AREA_A_001/events","{}",true));
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->receiver.receive("checkin/AREA_A_001/events","{} {}",false));
        } finally { receiver.stop(); }
    }
}
