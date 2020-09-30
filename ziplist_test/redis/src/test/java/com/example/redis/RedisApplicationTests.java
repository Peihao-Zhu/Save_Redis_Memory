package com.example.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.zip.CRC32;

@SpringBootTest
class RedisApplicationTests {

    @Test
    void contextLoads() {
    }

    @Autowired
    private RedisTemplate<String, String> strRedisTemplate;

    @Autowired
    private StringRedisTemplate listRedisTemplate;

    //    一千万数据
    private final static int count=10000000;
    //    list的数量
    private final static int key_count=30000;
    //    每个list存储的数量  为了让Redis使用ziplist，而不是linkedlist
    private final static int bucket_size=512;
    //    存储这1千万个uuid， 用于后续查询检测时使用
    private String[] str_uuid =new String[count];
    //    统计 每个list 对应的存储元素
    private HashMap<String,Integer> key_num=new HashMap<>();

    @Test
    public void test_key_value() {
        for(int i=0;i<count;i++){
            String uuid = UUID.randomUUID().toString().replaceAll("-","");
            str_uuid[i]=uuid;
            strRedisTemplate.opsForValue().set(uuid,String.valueOf(uuid.hashCode()));
        }

        System.out.println("-----------开始进行耗时检测-----------");
        //   进行10w次查询测试
        Random r = new Random();

        for(int j=0;j<5;j++) {
            long totalTime = 0;
            for (int i = 0; i < 100000; i++) {
                int index = r.nextInt(count);
                long startTime = System.currentTimeMillis();    //获取开始时间
                strRedisTemplate.opsForValue().get(str_uuid[index]);
                long endTime = System.currentTimeMillis();    //获取结束时间
                totalTime += (endTime - startTime);
            }
            System.out.println(j + 1 + "" + "次，用时：" + String.valueOf(totalTime));
        }
    }

    //   crc32将原先的32位uuid 转换成最多4位的string---30000 Redis中key时用string对象存储
    public String hashKey(String key){
        CRC32 crc32=new CRC32();
        crc32.update(key.getBytes());

        return crc32.getValue()%key_count+"";
    }


    @Test
    public void test_zipList() {

        for(int i=0;i<count;i++){
            String uuid = UUID.randomUUID().toString().replaceAll("-","");
            str_uuid[i]=uuid;
            String key = hashKey(uuid);
            if(key_num.containsKey(key)) {
                key_num.put(key,key_num.get(key)+1);
            }
            else{
                key_num.put(key,1);
            }

            int list_value=uuid.hashCode();
            listRedisTemplate.opsForList().leftPush(key, String.valueOf(list_value));
        }
        System.out.println("-----------判断每个list 的数目的分布情况-----------");
        for(String k :key_num.keySet()){
            System.out.println("key值："+k+"   列表对应的个数:"+key_num.get(k));
        }

        System.out.println("-----------开始进行耗时检测-----------");
        //   进行10w次查询测试
        Random r = new Random();

        for(int j=0;j<5;j++) {
            long totalTime=0;
            for (int i = 0; i < 100000; i++) {
                int index = r.nextInt(count);
                String key1 = hashKey(str_uuid[index]);
                long startTime = System.currentTimeMillis();    //获取开始时间
                List<String> list = listRedisTemplate.opsForList().range(key1, 0, -1);
                for (String x : list) {
                    if (String.valueOf(str_uuid[i].hashCode()) == x)
                        break;
                }
                long endTime = System.currentTimeMillis();    //获取结束时间
                totalTime += (endTime - startTime);
            }
            System.out.println(j+1+""+"次，用时："+String.valueOf(totalTime));
        }
    }

}
