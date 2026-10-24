// Additional TempGSI battery-temperature guard; never replaces OEM mitigation.
// Linux power_supply TEMP is in tenths of a degree Celsius.
#ifndef TEMPGSI_HOST_THERMAL_H
#define TEMPGSI_HOST_THERMAL_H
#include <ctype.h>
#define TEMP_START_MAX 380
#define TEMP_WARN 400
#define TEMP_RETURN 420
#define TEMP_SAMPLE_MS 2000
#define TEMP_BATTERY_PATH "/sys/class/power_supply/battery/temp"

static int parse_battery_temperature(const char *raw,int *temperature){
    char *end;errno=0;long value=strtol(raw,&end,10);
    if(end==raw||errno||value < -200||value > 1000)return -1;
    while(*end&&isspace((unsigned char)*end))end++;
    if(*end)return -1;
    *temperature=(int)value;return 0;
}
static int read_battery_temperature(int fd,int *temperature){
    char raw[64];ssize_t count=pread(fd,raw,sizeof(raw)-1,0);
    if(count<=0||count==(ssize_t)sizeof(raw)-1)return -1;
    raw[count]=0;return parse_battery_temperature(raw,temperature);
}
static int battery_temperature_action(int temperature,int starting){
    if(starting&&temperature>TEMP_START_MAX)return 2;
    if(temperature>=TEMP_RETURN)return 2;
    return temperature>=TEMP_WARN?1:0;
}
static int thermal_preflight(int fd){
    int temperature;
    if(fd<0||read_battery_temperature(fd,&temperature)){
        puts("REFUSE_GSI_BATTERY_TEMPERATURE_UNREADABLE");return -1;
    }
    printf("HOST_THERMAL_BATTERY_TENTHS_C=%d_START_MAX=%d_WARN=%d_RETURN=%d\n",temperature,TEMP_START_MAX,TEMP_WARN,TEMP_RETURN);
    if(battery_temperature_action(temperature,1)==2){puts("REFUSE_GSI_BATTERY_TOO_WARM_ALLOW_COOLING");return -1;}
    return 0;
}
static int thermal_self_test(void){
    int value=0;
    if(parse_battery_temperature("380\n",&value)||value!=380)return 1;
    if(!parse_battery_temperature("38000\n",&value)||!parse_battery_temperature("38C",&value)||!parse_battery_temperature("",&value)||!parse_battery_temperature("99999999999999999999",&value))return 2;
    if(battery_temperature_action(380,1)!=0||battery_temperature_action(381,1)!=2||battery_temperature_action(399,0)!=0||battery_temperature_action(400,0)!=1||battery_temperature_action(419,0)!=1||battery_temperature_action(420,0)!=2)return 3;
    char file[]="/data/local/tmp/tempgsi-thermal-test-XXXXXX";
    int fd=mkstemp(file);if(fd<0)return 4;unlink(file);
    int result=0;
    if(write(fd,"379\n",4)!=4||read_battery_temperature(fd,&value)||value!=379)result=5;
    if(!result&&(ftruncate(fd,0)||pwrite(fd,"420\n",4,0)!=4||read_battery_temperature(fd,&value)||battery_temperature_action(value,0)!=2))result=6;
    if(!result&&(ftruncate(fd,0)||!read_battery_temperature(fd,&value)))result=7;
    close(fd);
    if(!result)puts("THERMAL_SENSOR_PARSER_THRESHOLDS_HOT_AND_FAILED_SENSOR_SELF_TEST_OK_NO_SERVICES_CHANGED");
    return result;
}
#endif
