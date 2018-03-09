package com.github.bkm016.bsteams.database;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import com.github.bkm016.bsteams.BSTeamsPlugin;
import com.github.bkm016.bsteams.util.Config;

import lombok.Getter;
import me.skymc.taboolib.other.DateUtils;
import me.skymc.taboolib.playerdata.DataUtils;

public class TeamDataManager {
	
	public final static File DATA_FILE = new File("plugins" + File.separator + "BS-Teams" + File.separator + "Data.dat");
	
	@Getter
	private static YamlConfiguration data = new YamlConfiguration();
	
	@Getter
	private static ArrayList<TeamData> teamList = new ArrayList<TeamData>();
	
	@Getter
	private static BukkitRunnable runnable = null;

	// 玩家，邀请队长列表
	@Getter
	private static HashMap<String,List<String>> inviteMap = new HashMap<String,List<String>>();
	
	// 队长，申请玩家列表
	@Getter
	private static HashMap<String,List<String>> joinMap = new HashMap<String,List<String>>();
	
	//获取加入列表
	public static List<String> getjoinList(String playerName){
		List<String> joinList = new ArrayList<String>();
		if (joinMap.containsKey(playerName)){
			return joinMap.get(playerName);
		}else{
			joinMap.put(playerName, joinList);
		}
		return joinList;
	}
	
	//获取邀请列表
	public static List<String> getinviteList(String playerName){
		List<String> inviteList = new ArrayList<String>();
		if (inviteMap.containsKey(playerName)){
			return inviteMap.get(playerName);
		}else{
			inviteMap.put(playerName, new ArrayList<String>());
		}
		return inviteList;
	}
	
	// 定时清理joinMap
	static void ClearOverdueJoin(){
		for (String key : joinMap.keySet()){
			List<String> joinList = joinMap.get(key);
			for (int i = joinList.size()-1;i>0;i--){
				if(System.currentTimeMillis() > Long.valueOf(joinList.get(i).split(":")[1])){
					joinList.remove(i);
				}
			}
		}
	}
	
	// 定时清理inviteMap
	static void ClearOverdueInvite(){
		for (String key : inviteMap.keySet()){
			List<String> inviteList = inviteMap.get(key);
			for (int i = inviteList.size()-1;i>0;i--){
				if(System.currentTimeMillis() > Long.valueOf(inviteList.get(i).split(":")[1])){
					inviteList.remove(i);
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public static void loadData() {
		// 清空数据
		teamList.clear();
		
		// 检测数据库是否存在
		if (!DATA_FILE.exists()){
	        Bukkit.getConsoleSender().sendMessage("[BS-Teams] §c数据不存在，创建数据文件");
		}
		else {
			Bukkit.getConsoleSender().sendMessage("[BS-Teams] §7正在载入队伍数据...");
			try {
				data.load(DATA_FILE);
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		int removeTeamSize = 0;
		for (String teamLeader : data.getKeys(false)){
			Long teamTimes = data.getLong(teamLeader + ".Time");
			//当超时的时候
			if (teamTimes + DateUtils.formatDate(Config.getConfig(Config.TEAM_RETENTION_TIME)) < System.currentTimeMillis()){
				//删除队伍
				removeTeamSize++;
				data.set(teamLeader, null);
                continue;
			}
			// 载入成员
			List<String> teamMembers = data.getStringList(teamLeader + ".Members");
			// 载入物品
			List<ItemStack> teamItems = (List<ItemStack>) data.getList(teamLeader + ".Items");
			// 队伍数据
			TeamData teamData = new TeamData(teamLeader, teamMembers, teamItems, teamTimes);
			// 载入日志
			if (data.contains(teamLeader + ".Notes")) {
				for (String id : data.getConfigurationSection(teamLeader + ".Notes").getKeys(false)) {
					teamData.getItemNotes().add(new NoteData(
							data.getString(teamLeader + ".Notes." + id + ".Name"), 
							data.getString(teamLeader + ".Notes." + id + ".Item"),
							data.getLong(teamLeader + ".Notes." + id + ".Date")));
				}
			}
			// 载入设置
			if (data.contains(teamLeader + ".Options")) {
				for (String id : data.getConfigurationSection(teamLeader + ".Options").getKeys(false)) {
					teamData.setTeamOption(id, data.getBoolean(teamLeader + ".Options." + id));
				}
			}
			teamList.add(teamData);
		}
		Bukkit.getConsoleSender().sendMessage("[BS-Teams] §7已载入 §6"+teamList.size()+" §7条队伍数据");
		if (removeTeamSize>0){
			Bukkit.getConsoleSender().sendMessage("[BS-Teams] §7已清除 §c"+removeTeamSize+" §7条过时队伍数据");
		}
		

		// 清理加入、邀请过期数据
		// 保存任务
		new BukkitRunnable(){
			@Override
			public void run() {
				ClearOverdueInvite();
				ClearOverdueJoin();
				saveTeamList();
			}
		}.runTaskTimerAsynchronously(BSTeamsPlugin.getInst(), 600, 600);
	}
	
	/**
	 * 创建队伍
	 * 
	 * @param player 玩家
	 */
	public static void createTeam(Player player){
		teamList.add(new TeamData(player.getName(), null, null, null));
	}
	
	/**
	 * 获取玩家队伍
	 * 
	 * @param playerName 玩家名
	 * @return boolean
	 */
	public static TeamData getTeam(String playerName){
		for (TeamData teamData : teamList){
			if (teamData.getTeamLeader().equals(playerName) || teamData.getTeamMembers().contains(playerName)){
				return teamData;
			}
		}
		return null;
	}
	
	/**
	 * 是否为队长
	 * 
	 * @param playerName 玩家名
	 * @return boolean
	 */
	public static boolean isTeamLeader(String playerName){
		return getTeam(playerName) != null && getTeam(playerName).getTeamLeader().equals(playerName);
	}
	
	/**
	 * 删除队伍
	 * 
	 * @param teamData 队伍
	 */
	public static void removeTeam(TeamData teamData){
		if (teamList.contains(teamData)){
			teamList.remove(teamData);
		}
	}
	
	/**
	 * 保存队伍列表
	 */
	public static void saveTeamList(){
		Long oldTimes = System.nanoTime();
		// 清空数据
		try {
			data.loadFromString("");
		} catch (InvalidConfigurationException e) {
			// TODO Auto-generated catch block
		}
		// 遍历队伍
		for (TeamData teamData : teamList){
			String teamLeader = teamData.getTeamLeader();
			Long teamTimes = teamData.getTeamTimes();
			List<String> teamMembers = teamData.getTeamMembers();
			List<ItemStack> teamItems = teamData.getTeamItems();
			data.set(teamLeader+".Time", teamTimes);
			data.set(teamLeader+".Members", teamMembers);
			data.set(teamLeader+".Items", teamItems);
			// 提取日志
			int i = 0;
			for (NoteData note : teamData.getItemNotes()) {
				data.set(teamLeader + ".Notes." + i + ".Name", note.getPlayer());
				data.set(teamLeader + ".Notes." + i + ".Item", note.getItemName());
				data.set(teamLeader + ".Notes." + i + ".Date", note.getDate());
				i++;
			}
			for (Entry<String, Boolean> value : teamData.getTeamOptions().entrySet()) {
				data.set(teamLeader + ".Options." + value.getKey(), value.getValue());
			}
		}
		// 保存
		DataUtils.saveConfiguration(data, DATA_FILE);
		// 时间
		double endTimes = ((System.nanoTime() - oldTimes)/1000000D);
		// 提示
		BSTeamsPlugin.getLanguage().get("Admin.DataSaved")
			.addPlaceholder("$teams", String.valueOf(teamList.size()))
			.addPlaceholder("$time", String.valueOf(endTimes))
			.send(Bukkit.getConsoleSender());
	}

}
