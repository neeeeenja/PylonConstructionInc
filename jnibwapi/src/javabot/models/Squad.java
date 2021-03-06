package javabot.models;

import java.awt.Point;
import java.util.*;

import javabot.JavaBot;
import javabot.controllers.ArmyManager;
import javabot.controllers.BuildManager;
import javabot.controllers.ResourceManager;
import javabot.types.UnitType;
import javabot.types.UnitType.UnitTypes;
import javabot.util.*;

public class Squad {
	private ArrayList<Unit> squad;
	protected ArrayList<Unit> enemies;
	protected ArrayList<Unit> stragglers;
	public static final int ATTACKING = 0;
	public static final int DEFENDING = 1;
	public static final int IDLE = 2;
	public static final int RETREATING = 3;
	protected static final int NEARBY_RADIUS = 400;
	protected int status;
	protected Point rallyPoint;
	protected Point squadCenter;
	protected Point homeChokePoint;
	protected Point lastOrderPoint;

	private int updateCount = 0;
	
	public Squad() {
		status = IDLE;
		squad = new ArrayList<Unit>();
		enemies = new ArrayList<Unit>();
		stragglers = new ArrayList<Unit>();
		homeChokePoint = Utils.getClosestChokePoint(new Point(JavaBot.homePositionX, JavaBot.homePositionY));
		rallyPoint = new Point(-1, -1);
		lastOrderPoint = new Point(0, 0);
		squadCenter = new Point(-1, -1);
	}
	
	public void update() {
		updateCount++;
		updateSquadPos();
		if(squad.size() > 0) {
			moveStragglers();
			setEnemies(NEARBY_RADIUS);
			int combatScore = combatSimScore();
			if(updateCount % 100 == 0) {
				//JavaBot.bwapi.printText("combatSimScore: " + combatScore);
			}
			int newStatus = 0;
			Point latestOrder;
			
			if(squad.size() >= 2 && combatScore >= 10) {
				newStatus = ATTACKING;
				latestOrder = rallyPoint;
			}
			else {
				if(status != IDLE) {
					newStatus = RETREATING;
				}
				latestOrder = homeChokePoint;
			}
			if(status != newStatus) {
				JavaBot.bwapi.printText("New order");
				status = newStatus;
				lastOrderPoint = latestOrder;
				moveToRallyPoint(lastOrderPoint, status);
				if(status == ATTACKING) {
					JavaBot.bwapi.printText("New order: Attacking (" + lastOrderPoint.x + "," + lastOrderPoint.y + ")");
				}
				else if(status == DEFENDING) {
					JavaBot.bwapi.printText("New order: Defending (" + lastOrderPoint.x + "," + lastOrderPoint.y + ")");
				}
				else if(status == RETREATING) {
					JavaBot.bwapi.printText("New order: Retreating (" + lastOrderPoint.x + "," + lastOrderPoint.y + ")");
				}
			}
		}
	}
	
	public void act() {
		
	}
	
	protected void move(Point p) {
		for(Unit unit : squad) {
			JavaBot.bwapi.move(unit.getID(), p.x, p.y);
		}
		for(Unit unit : stragglers) {
			JavaBot.bwapi.move(unit.getID(), squadCenter.x, squadCenter.y);
		}
	}
	
	protected void moveToRallyPoint(Point p, int status) {
		for(Unit unit : squad) {
			if(status == ATTACKING) {
				JavaBot.bwapi.attack(unit.getID(), p.x, p.y);
			}
			else if(status == DEFENDING) {
				JavaBot.bwapi.move(unit.getID(), p.x, p.y);
			}
			else if(status == RETREATING) {
				JavaBot.bwapi.move(unit.getID(), p.x, p.y);
			}
		}
		for(Unit unit : stragglers) {
			JavaBot.bwapi.move(unit.getID(), squadCenter.x, squadCenter.y);
		}
	}
	
	protected void moveStragglers() {
		for(Unit unit : stragglers) {
			JavaBot.bwapi.move(unit.getID(), squadCenter.x, squadCenter.y);
		}
	}
	
	public void assignUnit(Unit unit) {
		if(squad.size() == 0) {
			squad.add(unit);
		}
		else {
			stragglers.add(unit);
		}
	}
	
	public int getStatus() {
		return status;
	}
	
	public void setStatus(int status) {
		this.status = status;
	}
	
	public int size() {
		return squad.size();
	}
	
	protected void setEnemies(int radius) {
		enemies.clear();
		for(Unit enemy : JavaBot.bwapi.getEnemyUnits()) {
			if (Utils.inRange(squadCenter, new Point(enemy.getX(), enemy.getY()), radius))
				enemies.add(enemy);
		}
	}
	
	public void setRallyPoint(Point p) {
		rallyPoint = (Point) p.clone();
	}
	
	public int combatSimScore() {
		int score = 0;
		for(Unit u : enemies) {
			UnitType type = JavaBot.bwapi.getUnitType(u.getTypeID());
			if(type.isWorker()) {
				//Worker is worthless atm
				score -= 0;
			}
			else if(type.isAttackCapable()) {
				score -= (u.getHitPoints()/type.getMaxHitPoints()) * type.getSupplyRequired() * 10;
			}
			else if(u.getTypeID() == UnitTypes.Terran_Bunker.ordinal()) {
				score -= (u.getHitPoints()/type.getMaxHitPoints()) * 200;
			}
		}
		for(Unit u : squad) {
			UnitType type = JavaBot.bwapi.getUnitType(u.getTypeID());
			//A full health zealot is worth 40
			//A little arbitrary. Just need to go back and forth until the right value is met in terms of worker/bunker worth to units
			score += (u.getHitPoints()/type.getMaxHitPoints()) * type.getSupplyRequired() * 10;
		}
		return score;
	}
	
	protected void updateSquadPos() {
		squadCenter = getCenter(squad);
		JavaBot.bwapi.drawCircle(squadCenter.x, squadCenter.y, NEARBY_RADIUS, BWColor.GREEN, false, false);
		
		for(Unit straggler : stragglers) {
			if(straggler.isCompleted()) {
				if(Utils.inRange(squadCenter, new Point(straggler.getX(), straggler.getY()), NEARBY_RADIUS)) {
					JavaBot.bwapi.printText("Straggler added to Squad");
					stragglers.remove(straggler);
					squad.add(straggler);
					moveToRallyPoint(lastOrderPoint, status);
				}
			}
		}
		squadCenter = getCenter(squad);
	}
	
	/**
	 * Removes unit from squad. This is called when Unit destroy event is fired in JavaBot
	 * @param unitId id of unit
	 * @return id of removed unit
	 */
	public int removeUnit(int unitId) {
		int id = -1;
		for (Unit unit : squad)
			if (unit.getID() == unitId) {
				squad.remove(unit);
				id = unitId;
				break;
			}
		
		return id;
	}
	
	/**
	 * Returns center of units
	 * @param units
	 * @return
	 */
	public static Point getCenter(ArrayList<Unit> units) {
		int x = 0, y = 0;
		
		if(units.size() >= 1) {
			for(Unit unit: units) {
				x += unit.getX();
				y += unit.getY();
			}
			x = x / units.size();
			y = y / units.size();
		}
		return new Point(x,y);
	}

}
