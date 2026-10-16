/**
 *  Copyright (C) 2002-2024   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.model;

import static net.sf.freecol.common.util.CollectionUtils.transform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.common.util.LogBuilder;


/**
 * Utility class for determining occupations within a colony.
 */
public final class OccupationPlanner {

    private static final Logger logger =
            Logger.getLogger(OccupationPlanner.class.getName());

    /** The colony being planned. */
    private final Colony colony;


    /**
     * Creates a new occupation planner.
     *
     * @param colony The colony to plan occupations for.
     */
    public OccupationPlanner(Colony colony) {
        this.colony = colony;
    }

    /**
     * Gets the best occupation for a given unit.
     *
     * Candidate goods types are evaluated in priority order until a
     * suitable occupation is found.
     *
     * @param unit The {@code Unit} to find an
     *     {@code Occupation} for.
     * @param userMode If a user requested this, favour the current
     *     work type, if not favour goods that the unit requires.
     * @return An {@code Occupation} for the given unit, or
     *     null if none found.
     */
    public Occupation findBestOccupation(Unit unit, boolean userMode) {
        LogBuilder lb = new LogBuilder(
                colony.getOccupationTrace() ? 64 : 0);

        lb.add(colony.getName(), ".findBestOccupation(", unit, ")");

        for (Collection<GoodsType> types
                : getWorkTypeChoices(unit, userMode)) {

            lb.add("\n  ");
            FreeColObject.logFreeColObjects(types, lb);

            Occupation occupation =
                    findBestOccupationForTypes(unit, types, lb);

            if (occupation != null) {
                lb.log(logger, Level.WARNING);
                return occupation;
            }
        }

        lb.add("\n  => FAILED");
        lb.log(logger, Level.WARNING);

        return null;
    }

    /**
     * Get a list of collections of goods types, in order of priority
     * to try to produce in this colony by a given unit.
     *
     * @param unit The {@code Unit} to check.
     * @param userMode If a user requested this, favour the current
     *     work type, if not favour goods that the unit requires.
     * @return The list of collections of {@code GoodsType}s.
     */
    public List<Collection<GoodsType>> getWorkTypeChoices(
            Unit unit, boolean userMode) {

        final Specification spec = colony.getSpecification();

        List<Collection<GoodsType>> result = new ArrayList<>();
        Set<GoodsType> tried = new HashSet<>();

        // Find the food and non-food goods types required by this unit
        // and which are underproduced at present.
        Set<GoodsType> food = new HashSet<>();
        Set<GoodsType> nonFood = new HashSet<>();

        for (AbstractGoods ag : transform(
                unit.getType().getConsumedGoods(),
                g -> colony.getNetProductionOf(g.getType())
                        < g.getAmount())) {

            if (ag.isFoodType()) {
                food.addAll(ag.getType().getEquivalentTypes());
            } else {
                nonFood.addAll(ag.getType().getEquivalentTypes());
            }
        }

        if (userMode) { // Favour current and expert types in user mode
            accumulateChoice(unit.getWorkType(), tried, result);
            accumulateChoice(unit.getType().getExpertProduction(), tried, result);
            accumulateChoice(unit.getExperienceType(), tried, result);
            accumulateChoices(food, tried, result);
            accumulateChoices(nonFood, tried, result);
        } else { // Otherwise favour the required goods types
            accumulateChoices(food, tried, result);
            accumulateChoices(nonFood, tried, result);
            accumulateChoice(unit.getWorkType(), tried, result);
            accumulateChoice(unit.getType().getExpertProduction(), tried, result);
            accumulateChoice(unit.getExperienceType(), tried, result);
        }
        accumulateChoices(spec.getFoodGoodsTypeList(), tried, result);
        accumulateChoices(spec.getNewWorldLuxuryGoodsTypeList(), tried, result);
        accumulateChoices(spec.getGoodsTypeList(), tried, result);

        return result;
    }

    /**
     * Gets the best occupation for a given unit to produce one of
     * a given set of goods types.
     *
     * Every current work location in the colony is evaluated and the
     * most productive resulting occupation is retained.
     *
     * @param unit The {@code Unit} to find an
     *     {@code Occupation} for.
     * @param workTypes A collection of {@code GoodsType} to
     *     consider producing.
     * @param lb A {@code LogBuilder} to log to.
     * @return An {@code Occupation} for the given unit, or null
     *     if none found.
     */
    private Occupation findBestOccupationForTypes(
            Unit unit,
            Collection<GoodsType> workTypes,
            LogBuilder lb) {

        if (workTypes.isEmpty()) return null;

        Occupation best =
                new Occupation(null, null, null);

        int bestAmount = 0;

        for (WorkLocation wl
                : colony.getCurrentWorkLocationsList()) {

            bestAmount =
                    best.improve(unit,
                            wl,
                            bestAmount,
                            workTypes,
                            lb);
        }

        if (best.workLocation != null) {
            lb.add("\n  => ",
                    best,
                    " = ",
                    bestAmount);
        }

        return (best.workLocation == null)
                ? null
                : best;
    }

    /**
     * Adds a collection of goods types to the result set,
     * excluding any goods types already considered.
     *
     * @param workTypes The candidate goods types.
     * @param tried The goods types already processed.
     * @param result The destination choice list.
     */
    private void accumulateChoices(
            Collection<GoodsType> workTypes,
            Collection<GoodsType> tried,
            List<Collection<GoodsType>> result) {

        workTypes.removeAll(tried);

        if (!workTypes.isEmpty()) {
            result.add(workTypes);
            tried.addAll(workTypes);
        }
    }

    /**
     * Adds a goods type and all of its equivalent types to the
     * accumulated choice list.
     *
     * @param workType The goods type to add.
     * @param tried The goods types already processed.
     * @param result The destination choice list.
     */
    private void accumulateChoice(
            GoodsType workType,
            Collection<GoodsType> tried,
            List<Collection<GoodsType>> result) {

        if (workType == null) return;

        accumulateChoices(
                workType.getEquivalentTypes(),
                tried,
                result);
    }
}
