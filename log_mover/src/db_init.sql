create table if not exists last_read (
  l_r_mover varchar(100) primary key,
  l_r_file varchar(100),
  l_r_offset bigint
) engine = innodb;

create table if not exists last_write (
  l_w_mover varchar(100) primary key,
  l_w_file varchar(100)
) engine = innodb;

create table if not exists vmstat (
  v_hostname varchar(200),
  v_date datetime,
  boot_time integer,
  free_memory integer,
  pages_swapped_in integer,
  used_swap integer,
  free_swap integer,
  idle_cpu_ticks bigint,
  total_memory integer,
  pages_paged_in bigint,
  irq_cpu_ticks integer,
  swap_cache integer,
  buffer_memory integer,
  used_memory integer,
  pages_swapped_out integer,
  forks integer,
  nice_user_cpu_ticks integer,
  interrupts bigint,
  io_wait_cpu_ticks integer,
  system_cpu_ticks integer,
  cpu_context_switches bigint,
  inactive_memory integer,
  active_memory integer,
  softirq_cpu_ticks integer,
  pages_paged_out bigint,
  non_nice_user_cpu_ticks integer,
  stolen_cpu_ticks integer,
  total_swap integer
) engine = innodb;

create table if not exists iostat (
  io_hostname varchar(200),
  io_date datetime,
  io_cpu_system float,
  io_cpu_iowait float,
  io_cpu_steal float,
  io_cpu_user float,
  io_cpu_idle float,
  io_disk_write_s float,
  io_disk_write float,
  io_disk_read float,
  io_disk_tps float,
  io_disk_read_s float,
  io_cpu_nice float
) engine = innodb;

create table if not exists net_io (
  n_hostname varchar(200),
  n_date datetime,
  n_in_fifo integer,
  n_in_drop integer, 
  n_out_compressed integer,
  n_out_carrier integer,
  n_out_fifo integer,
  n_in_frame integer,
  n_in_bytes bigint,
  n_in_multicast integer,
  n_in_errs integer,
  n_out_errs integer,
  n_in_compressed integer,
  n_in_packets bigint,
  n_out_bytes bigint,
  n_out_packets bigint,
  n_out_drop integer,
  n_out_colls integer
) engine = innodb;

create table if not exists hadoop (
  h_id bigint primary key auto_increment,
  h_hostname varchar(200),
  h_date datetime,
  h_category varchar(100),
  h_class varchar(255),
  h_level varchar(25),
  h_message text
) engine = innodb;

create table if not exists hadoop_extras (
  h_e_h_id bigint,
  h_e_order integer,
  h_e_message text
) engine = innodb;

create table if not exists mpstat (
  m_hostname varchar(200),
  m_date datetime,
  m_intr_s float,
  m_soft float,
  m_irq float,
  m_idle float,
  m_sys float,
  m_user float,
  m_nice float,
  m_steal float,
  m_iowait float
) engine = innodb;
