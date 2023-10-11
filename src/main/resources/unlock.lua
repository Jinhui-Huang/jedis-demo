-- 锁的key
-- local key = KEYS[1]
-- 当前线程标示
-- local threadId = ARGV[1]

-- 比较线程标示与锁中的标示是否一致
if (redis.call('get', KEYS[1])== ARGV[1]) then
    -- 释放锁 del key
    return redis.call('del', KEYS[1])
end
return 0