import com.apogames.kitchenchef.ai.Cooking;
import com.apogames.kitchenchef.ai.KitchenInformation;
import com.apogames.kitchenchef.ai.KitchenPlayerAI;
import com.apogames.kitchenchef.ai.Student;
import com.apogames.kitchenchef.ai.action.Action;
import com.apogames.kitchenchef.ai.actionPoints.ActionPoint;
import com.apogames.kitchenchef.ai.player.Player;
import com.apogames.kitchenchef.game.entity.Vector;
import com.apogames.kitchenchef.game.enums.*;
import com.apogames.kitchenchef.game.pathfinding.PathResult;

import java.util.*;

public class MyBot extends KitchenPlayerAI {
    Helper helper = null;
    public KitchenInformation information;

    @Student(
            author = "Tim Wernecke",
            matrikelnummer = 232916
    )

    @Override
    public String getName() {
        return "changecounter: 9";
    }

    @Override
    public void update(KitchenInformation kitchenInformation, List<Player> playerList) {
        this.information = kitchenInformation;
        if (this.helper == null) {
            this.helper = new Helper(kitchenInformation, playerList);
        } else {
            this.helper.update(kitchenInformation, playerList);
        }

        Queue<ActionPoint> foodBoilings = this.helper.foodBoiling();
        Queue<ActionPoint> foodReady = this.helper.foodReady();
        for (int i = 0; i < playerList.size(); i++) {
            if (hasRecipe(playerList.get(i))) {
                fulfillRecipe(playerList.get(i), i);
            } else {
                if (!foodReady.isEmpty()) {
                    ActionPoint cooking = foodReady.peek();
                    goToActionPoint(cooking, playerList.get(i), i, true);
                    foodReady.remove();
                } else if (!foodBoilings.isEmpty()) {
                    if (this.helper.dirtyDishes() && !Helper.playerDestinations.contains(this.helper.getDishWashing())) {
                        goToActionPoint(this.helper.getDishWashing(), playerList.get(i), i, true);
                    }else {
                        goToActionPoint(Helper.cookingStations.get(0), playerList.get(i), i, false);
                    }
                    foodBoilings.remove();
                } else {
                    goToActionPoint(this.helper.getNextCustomer(i), playerList.get(i), i, true);
                }
            }
        }
    }

    /**
     * @param player Current player
     * @return Returns if a player has a recipe
     */
    private boolean hasRecipe(Player player) {
        return player.getCooking() != null;
    }

    /**
     * Helper function to check the current Recipe status for a given player
     * @param player Current player
     * @param playerId Player ID, equal to list order
     */
    private void fulfillRecipe(Player player, int playerId) {
        Cooking cooking = player.getCooking();
        if (cooking == null) {
            System.out.println("what | L:33");
            return;
        }
        CookingStatus cookingStatus = cooking.getStatus();

        if (cookingStatus.equals(CookingStatus.NEEDED_DISH)) {
            if (helper.dishExisting(cooking.getRecipe().getDish())) {
                this.goToActionPoint(this.helper.getDishTaking(), player, playerId, true);
            } else {
                this.goToActionPoint(this.helper.getDishWashing(), player, playerId, true);
            }
        } else if (cookingStatus.equals(CookingStatus.DISH) || cookingStatus.equals(CookingStatus.RAW)) {
            dishDishWishWish(player, playerId);
        } else if (cookingStatus.equals(CookingStatus.READY_FOR_CUTTING)) {
            boolean used = false;
            for (ActionPoint cutting : Objects.requireNonNull(this.helper.getCuttingStations())) {
                if (Helper.playerDestinations.contains(cutting) && Helper.playerDestinations.indexOf(cutting) == playerId) {
                    this.goToActionPoint(cutting, player, playerId, true);
                    used = true;
                }
                if (!Helper.playerDestinations.contains(cutting)) {
                    this.goToActionPoint(cutting, player, playerId, true);
                    used = true;
                }
            }
            if (!used) {
                this.goToActionPoint(this.helper.getActionPointByEnum(KitchenActionPointEnum.CUTTING), player, playerId, false);
            }
        } else if (cookingStatus.equals(CookingStatus.READY_FOR_COOKING)) {
            if(Helper.cookingRightNow.size() > playerId) {
                ActionPoint cookingStation = this.helper.nextCookingStation(playerId);
                Helper.cookingRightNow.set(playerId, cooking);
                this.goToActionPoint(cookingStation, player, playerId, true);
            }

        } else if (cookingStatus.equals(CookingStatus.SERVEABLE)) {
            ActionPoint customer = this.helper.getActionPointByPosition(player.getCooking().getCustomerPosition());
            this.goToActionPoint(customer, player, playerId, true);
        }
    }

    /**
     * Helper function to perform DISH && RAW state
     * @param player   Current Player
     * @param playerId Player ID, equal to list order
     */
    private void dishDishWishWish(Player player, int playerId) {
        List<KitchenIngredient> neededIngredients = player.getCooking().getRecipe().getNeededIngredients();

        for (KitchenIngredient ing : player.getCooking().getIngredients()) {
            neededIngredients.remove(ing);
        }

        if (!neededIngredients.isEmpty() && !this.helper.trueList(player.getCooking().getIngredientsCorrect())) {
            ActionPoint ingredientStation = this.helper.getUpdatedIngredientStation(player, neededIngredients);
            if (ingredientStation != null) {
                this.goToActionPoint(ingredientStation, player, playerId, true);
            } else {
                if (Helper.playerDestinations.contains(this.helper.getBuyArea()) &&
                        Helper.playerDestinations.indexOf(this.helper.getBuyArea()) != playerId) {
                    this.goToActionPoint(this.helper.getActionPointByEnum(KitchenActionPointEnum.INGREDIENT_TAKE),
                            player, playerId, false);
                } else {
                    this.goToActionPoint(this.helper.getBuyArea(), player, playerId, true);
                }
            }
        } else {
            List<KitchenSpice> neededSpice = player.getCooking().getRecipe().getNeededSpice();
            if (!neededSpice.isEmpty()) {
                if (this.helper.enoughSpice(neededSpice)) {
                    ActionPoint nextStation = this.helper.nextSpice(player, neededSpice);
                    assert nextStation != null;

                    this.goToActionPoint(nextStation, player, playerId, !Helper.playerDestinations.contains(nextStation) ||
                            Helper.playerDestinations.indexOf(nextStation) == playerId);
                } else {
                    if (Helper.playerDestinations.contains(this.helper.getBuyArea()) &&
                            Helper.playerDestinations.indexOf(this.helper.getBuyArea()) != playerId) {
                        this.goToActionPoint(this.helper.getActionPointByEnum(KitchenActionPointEnum.SPICE_TAKE),
                                player, playerId, false);
                    } else {
                        this.goToActionPoint(this.helper.getActionPointByEnum(KitchenActionPointEnum.BUY), player, playerId, true);
                        this.goToActionPoint();
                    }
                }
            }
        }
    }

    /**
     * Moves a player to an action point, uses the action point (optional)
     * @param point    Action point to use
     * @param player   Current Player
     * @param playerId Player ID, equal to list order
     * @param usePoint change player action to Action.use()
     */
    private void goToActionPoint(ActionPoint point, Player player, int playerId, boolean usePoint) {
        if (point == null) {
            return;
        }
        if (usePoint && Helper.playerDestinations.contains(point) &&
                Helper.playerDestinations.get(playerId) != point) {
            Helper.playerDestinations.set(playerId, point);
        }

        if (point.isPlayerIn(player)) {
            if (usePoint) {
                player.setAction(Action.use());
            }
            return;
        }

        PathResult pathResult = this.information.getWays().findWayFromTo(this.information,
                player, point.getPosition());
        player.setAction(Action.move(pathResult.getMovement()));
    }

    /**
     * Helper class to store information
     */
    private final static class Helper {
        public static final List<ActionPoint> playerDestinations = new ArrayList<>();
        public static final List<Cooking> cookingRightNow = new ArrayList<>();

        private static final List<ActionPoint> ingredientTakeStations = new ArrayList<>();
        private static final List<ActionPoint> spiceTakeStations = new ArrayList<>();
        private static final List<ActionPoint> cuttingStations = new ArrayList<>();
        private static final List<ActionPoint> cookingStations = new ArrayList<>();
        private static ActionPoint DISH_TAKING;
        private static ActionPoint DISH_WASHING;
        private static ActionPoint BUY_AREA;
        private static boolean init = false;
        KitchenInformation information;
        int playerSize;

        /**
         * Constructor
         * @param information Current KitchenInformation
         * @param playerList List of players to use
         */
        public Helper(KitchenInformation information, List<Player> playerList) {
            this.information = information;
            this.playerSize = playerList.size();
            if (!init) {
                init();
                init = true;
            } else {
                updateIngredients_Spice(information);
            }
        }

        /**
         * Init helper functions
         */
        private void init() {
            for (ActionPoint point : this.information.getActionPoints()) {
                switch (point.getContent()) {
                    case CUTTING:
                        cuttingStations.add(point);
                        break;
                    case COOKING:
                        cookingStations.add(point);
                        break;
                    case INGREDIENT_TAKE:
                        ingredientTakeStations.add(point);
                        break;
                    case SPICE_TAKE:
                        spiceTakeStations.add(point);
                        break;
                    case DISH_TAKING:
                        DISH_TAKING = point;
                        break;
                    case DISH_WASHING:
                        DISH_WASHING = point;
                        break;
                    case BUY:
                        BUY_AREA = point;
                        break;
                }
            }
            for (int i = 0; i < playerSize; i++) {
                cookingRightNow.add(null);
                playerDestinations.add(null);
            }
            System.out.println("ready");
        }

        /**
         * @return List of cutting stations from current map
         */
        public List<ActionPoint> getCuttingStations() {
            return new ArrayList<>(cuttingStations);
        }

        /**
         * clears Helper-Lists
         */
        private void clearData() {
            ingredientTakeStations.clear();
            spiceTakeStations.clear();
            cuttingStations.clear();
            cookingStations.clear();
        }

        /**
         * Update function for helper data
         * @param information Current KitchenInformation
         * @param playerList List of players to use
         */
        public void update(KitchenInformation information, List<Player> playerList) {
            this.information = information;
            this.playerSize = playerList.size();
            this.clearData();
            for (ActionPoint point : information.getActionPoints()) {
                switch (point.getContent()) {
                    case CUTTING:
                        cuttingStations.add(point);
                        break;
                    case COOKING:
                        cookingStations.add(point);
                        break;
                    case INGREDIENT_TAKE:
                        ingredientTakeStations.add(point);
                        break;
                    case SPICE_TAKE:
                        spiceTakeStations.add(point);
                        break;
                    case DISH_TAKING:
                        DISH_TAKING = point;
                        break;
                    case DISH_WASHING:
                        DISH_WASHING = point;
                        break;
                }
            }
        }

        /**
         * Helper function to update only ingredient- and spice-take stations
         * @param information Current KitchenInformation
         */
        public void updateIngredients_Spice(KitchenInformation information) {
            this.information = information;
            ingredientTakeStations.clear();
            spiceTakeStations.clear();
            for (ActionPoint point : information.getActionPoints()) {
                switch (point.getContent()) {
                    case INGREDIENT_TAKE:
                        ingredientTakeStations.add(point);
                        break;
                    case SPICE_TAKE:
                        spiceTakeStations.add(point);
                        break;
                }
            }
        }

        /**
         * @param dish Dish object to use
         * @return Given dish exists at DISH_TAKING
         */
        public boolean dishExisting(KitchenDish dish) {
            return DISH_TAKING.getDishes().contains(dish);
        }

        /**
         * @return Returns the DISH_TAKING Action-Point
         */
        public ActionPoint getDishTaking() {
            return DISH_TAKING;
        }

        /**
         * @return Returns the BUY_AREA Action-Point
         */
        public ActionPoint getBuyArea() {
            return BUY_AREA;
        }

        /**
         * @return Returns the DISH_WASHING Action-Point
         */
        public ActionPoint getDishWashing() {
            return DISH_WASHING;
        }

        /**
         * @param type KitchenActionPointEnum type
         * @return Returns the first Action-Point found, matching given type
         */
        public ActionPoint getActionPointByEnum(KitchenActionPointEnum type) {
            for (ActionPoint point : this.information.getActionPoints()) {
                if (point.getContent() == type) {
                    return point;
                }
            }
            return null;
        }

        /**
         * Calculates the next customer by customer waiting-time
         * @param playerId PlayerID from player list to prevent multi-order-getting
         * @return Returns the next customer to visit
         */
        public ActionPoint getNextCustomer(int playerId) {
            float maxWaitTime = -1F;
            ActionPoint nextCustomer = null;

            for (ActionPoint point : this.information.getActionPoints()) {
                if (point.getContent() == KitchenActionPointEnum.CUSTOMER) {
                    if (point.isCustomerWaiting() && !point.wasVisited() && point.getWaitingTime() > maxWaitTime) {
                        if (playerDestinations.contains(point)) {
                            if (playerDestinations.indexOf(point) == playerId) {
                                nextCustomer = point;
                                maxWaitTime = point.getWaitingTime();
                            }
                        } else {
                            nextCustomer = point;
                            maxWaitTime = point.getWaitingTime();
                        }
                    }
                }
            }
            return nextCustomer;
        }

        /**
         * @param list List of Booleans
         * @return Returns weather a list of booleans contains only true
         */
        public boolean trueList(List<Boolean> list) {
            for (boolean b : list) {
                if (!b) return false;
            }
            return true;
        }

        /**
         * Calculates the closest Action-Point from a given player and a given Action-Point-Type
         * @param player Current player
         * @param type KitchenActionPointEnum type
         * @return Returns the closest ActionPoint
         */
        public ActionPoint closestActionPoint(Player player, KitchenActionPointEnum type) {
            ActionPoint closestPoint = null;
            float minDistance = Float.MAX_VALUE;
            for (ActionPoint point : this.information.getActionPoints()) {
                if (point.getContent().equals(type) && point.getPosition().distance(player.getPosition()) < minDistance) {
                    minDistance = point.getPosition().distance(player.getPosition());
                    closestPoint = point;
                }
            }
            return closestPoint;
        }

        /**
         * Calculates the next cookingStation that isn't used at the moment
         * @param playerId PlayerID from player list to prevent deadlock
         * @return Returns the next cookingStation
         */
        public ActionPoint nextCookingStation(int playerId) {
            for (ActionPoint point : cookingStations) {
                if (playerDestinations.contains(point)) {
                    if (playerDestinations.indexOf(point) == playerId) {
                        if (!point.isUsedAtTheMoment()) {
                            return point;
                        }
                    }
                } else {
                    if (!point.isUsedAtTheMoment()) {
                        return point;
                    }
                }
            }
            return null;
        }

        /**
         * @param list List to copy
         * @return Returns a copy of a given KitchenIngredientList
         */
        private List<KitchenIngredient> copyIngredients(List<KitchenIngredient> list) {
            if (list.isEmpty()) return null;
            return new ArrayList<>(list);
        }

        /**
         * @param spices List to copy
         * @return Returns a copy of a given KitchenSpiceList
         */
        private List<KitchenSpice> copySpice(List<KitchenSpice> spices) {
            if (spices.isEmpty()) return new ArrayList<>();
            return new ArrayList<>(spices);
        }

        /**
         * @param neededSpice List of KitchenSpice needed to cook the meal
         * @return Returns if there are enough spices, all spice-take-stations combined
         */
        public boolean enoughSpice(List<KitchenSpice> neededSpice) {
            List<KitchenSpice> neededOK = this.copySpice(neededSpice);
            for (ActionPoint p : spiceTakeStations) {
                List<KitchenSpice> currSpices = p.getSpices();
                for (KitchenSpice currSpice : currSpices) {
                    neededOK.remove(currSpice);
                }
            }
            return neededOK.isEmpty();
        }

        /**
         * @return Returns if there are dirty dishes left
         */
        public boolean dirtyDishes() {
            return !DISH_WASHING.getDishes().isEmpty();
        }

        /**
         * @param pos ActionPoint postion
         * @return Calculates the ActionPoint by a given Position
         */
        public ActionPoint getActionPointByPosition(Vector pos) {
            ActionPoint closest = null;
            float minDist = Float.MAX_VALUE;
            for (ActionPoint point : this.information.getActionPoints()) {
                if (point.getPosition().distance(pos) < minDist) {
                    minDist = point.getPosition().distance(pos);
                    closest = point;
                }
            }
            return closest;
        }

        /**
         * @return Returns a Queue<ActionPoint> of every boiling meal
         */
        public Queue<ActionPoint> foodBoiling() {
            Queue<ActionPoint> boilingFoods = new LinkedList<>();
            for (Cooking c : this.information.getCookings()) {
                if (c.getPosition() != null) {
                    boilingFoods.add(getActionPointByPosition(c.getPosition()));
                }
            }
            return boilingFoods;
        }

        /**
         * @return Returns a Queue<ActionPoint> of every meal ready to serve / rotten
         */
        public Queue<ActionPoint> foodReady() {
            Queue<ActionPoint> boilingFoods = new LinkedList<>();
            for (Cooking c : this.information.getCookings()) {
                if (c.getWaitHelper() != null && c.getPosition() != null) {
                    if (c.getWaitHelper().isRotten() || c.getWaitHelper().isReady()) {
                        boilingFoods.add(getActionPointByPosition(c.getPosition()));
                    }
                }
            }
            return boilingFoods;
        }

        /**
         * @param point ActionPoint to check
         * @param neededSpices List of needed Spice
         * @return Calculates if a given ActionPoint got any spice
         */
        public boolean actionPointGotSpice(ActionPoint point, List<KitchenSpice> neededSpices) {
            for (KitchenSpice ing : point.getSpices()) {
                if (neededSpices.contains(ing)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * @param player Current Player
         * @param neededSpices List of needed Spices
         * @return Calculates the next ActionPoint to get Spices
         */
        public ActionPoint nextSpice(Player player, List<KitchenSpice> neededSpices) {
            ActionPoint closestActionPoint = this.closestActionPoint(player, KitchenActionPointEnum.SPICE_TAKE);
            if (actionPointGotSpice(closestActionPoint, neededSpices)) {
                return closestActionPoint;
            } else {
                if (!spiceTakeStations.isEmpty()) {
                    for (ActionPoint point : spiceTakeStations) {
                        if (point != closestActionPoint && actionPointGotSpice(point, neededSpices)) {
                            return point;
                        }
                    }
                }
                return null;
            }
        }

        /**
         * @param player Current Player
         * @param neededIngredients List of needed Ingredients
         * @return Calculates the next ActionPoint to get Ingredients
         */
        public ActionPoint getUpdatedIngredientStation(Player player, List<KitchenIngredient> neededIngredients) {
            ActionPoint bestIngTake = bestIngredientTake(neededIngredients, player);
            if (bestIngTake != null) {
                return bestIngTake;
            }
            for (ActionPoint point : ingredientTakeStations) {
                for (KitchenIngredient ing : point.getIngredients()) {
                    if (neededIngredients.contains(ing)) {
                        return point;
                    }
                }
            }
            return null;
        }

        /**
         * @param _neededIngredients List of needed Ingredients
         * @param player Current player
         * @return Calculates the best ActionPoint to take ingredients
         */
        public ActionPoint bestIngredientTake(List<KitchenIngredient> _neededIngredients, Player player) {
            List<KitchenIngredient> neededIngredients;
            List<ActionPoint> holdMaBeer = new ArrayList<>();
            for (ActionPoint point : ingredientTakeStations) {
                neededIngredients = this.copyIngredients(_neededIngredients);
                for (KitchenIngredient ing : point.getIngredients()) {
                    assert neededIngredients != null;
                    neededIngredients.remove(ing);
                }
                assert neededIngredients != null;
                if (neededIngredients.isEmpty()) {
                    holdMaBeer.add(point);
                }
            }
            if (holdMaBeer.size() == 1) {
                return holdMaBeer.get(0);
            }
            if (holdMaBeer.size() < 1) {
                return null;
            }

            float minDist = Float.MAX_VALUE;
            ActionPoint ret = null;
            for (ActionPoint point : holdMaBeer) {
                if (point.getPosition().distance(player.getPosition()) < minDist) {
                    minDist = point.getPosition().distance(player.getPosition());
                    ret = point;
                }
            }
            return ret;
        }
    }
}
