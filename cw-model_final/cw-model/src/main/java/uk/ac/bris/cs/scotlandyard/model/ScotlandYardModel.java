package uk.ac.bris.cs.scotlandyard.model;

import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.BLACK;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;


public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move> {

    private List<Boolean> rounds;
    private Graph<Integer, Transport> graph;
    private List<ScotlandYardPlayer> syplayers;
    private int currentRound;
    private int currentPlayerIndex;
    private int mrXLastKnown;
    private List<Spectator> spectators;

    public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
                             PlayerConfiguration mrX, PlayerConfiguration firstDetective,
                             PlayerConfiguration... restOfTheDetectives) {

        this.rounds = requireNonNull(rounds);
        this.graph = requireNonNull(graph);
        this.syplayers = new ArrayList<>();
        this.currentRound = ScotlandYardView.NOT_STARTED;
        this.spectators = new ArrayList<>();
        this.currentPlayerIndex = 0;
        this.mrXLastKnown = 0;

        if (rounds.isEmpty()) {
            throw new IllegalArgumentException("The list of rounds cannot be empty.");
        }

        if (graph.isEmpty()) {
            throw new IllegalArgumentException("The game graph cannot be empty.");
        }

        if (mrX.colour != Colour.BLACK) {
            throw new IllegalArgumentException("Mr X's colour should only be BLACK.");
        }

        // Adds all detectives including the first detective, and Mr X to a list of player configurations.
        ArrayList<PlayerConfiguration> configurations = new ArrayList<>();

        for (PlayerConfiguration configuration : restOfTheDetectives) {
            configurations.add(requireNonNull(configuration));
        }
        configurations.add(0, firstDetective);
        configurations.add(0, mrX);


        // Adds all the player locations to a set, whilst checking no duplicate locations.
        Set<Integer> set_location = new HashSet<>();
        for (PlayerConfiguration configuration : configurations) {
            if (set_location.contains(configuration.location))
                throw new IllegalArgumentException("Two players cannot start at the same location.");
            set_location.add(configuration.location);
        }


        // Does the same thing with colours.
        Set<Colour> set_colour = new HashSet<>();
        for (PlayerConfiguration configuration : configurations) {
            if (set_colour.contains(configuration.colour))
                throw new IllegalArgumentException("Two players cannot have the same colour.");
            set_colour.add(configuration.colour);
        }

        // Checks all the players have all the types of tickets.
        for (PlayerConfiguration configuration : configurations) {
            if (!((configuration.tickets.containsKey(Ticket.DOUBLE)) && (configuration.tickets
                    .containsKey(Ticket.SECRET)) && (configuration.tickets.containsKey(Ticket
                    .BUS)) && (configuration.tickets.containsKey(Ticket.UNDERGROUND)) &&
                    (configuration.tickets.containsKey(Ticket.TAXI)))) {
                throw new IllegalArgumentException(" A Player is missing a ticket type.");
            }
        }


        // Checks the detetctives do not have > 0 SECRET or DOUBLE tickets.
        for (PlayerConfiguration configuration : configurations) {
            if (((configuration.tickets.get(Ticket.SECRET) != 0) || (configuration.tickets.get
                    (Ticket.DOUBLE) != 0)) && configuration.colour != Colour.BLACK) {
                throw new IllegalArgumentException("A detective has a SECRET or DOUBLE.");
            }
        }

        // Adds the player configurations to a a list of ScotlandYardPlayers.
        for (PlayerConfiguration configuration : configurations) {
            this.syplayers.add(new ScotlandYardPlayer(configuration.player, configuration.colour,
                    configuration.location, configuration.tickets));
        }
    }


    // Adds a spectator to the list of spectatators. Checks
    // that the spectatator has not already been registered and is not NULL.
    @Override
    public void registerSpectator(Spectator spectator) {
        if (spectator == null) throw new NullPointerException("NULL spectator.");
        else if (spectators.contains(spectator))
            throw new IllegalArgumentException("Cannot register a spectator more than once.");
        else spectators.add(spectator);
    }


    // Removes a spectatator. Also checks for NULL and that the specatator is already
    //registred.
    @Override
    public void unregisterSpectator(Spectator spectator) {
        if (spectator == null) throw new NullPointerException("NULL spectator.");
        else if (!spectators.contains(spectator))
            throw new IllegalArgumentException("Cannot de-register an unregistered spectator.");
        else spectators.remove(spectator);
    }


    // Takes a colour and returns a ScotlandYardPlayer by cycling through all the
    // ScotlandYardPlayers and cross checking for the colour given.
    private ScotlandYardPlayer colourToPlayer(Colour colour) {
        for (ScotlandYardPlayer syplayer : syplayers) {
            if (syplayer.colour() == colour) {
                return syplayer;
            }
        }
        return null;
    }


    //Checks that the game is not over and then gets all the parametres for .makeMove
    // and then calls .makeMove on the current player.
    @Override
    public void startRotate() {

        if (isGameOver()) throw new IllegalStateException("Cannot rotate when game is over.");

        Colour colour = getCurrentPlayer();
        ScotlandYardPlayer player = colourToPlayer(colour);
        int location = player.location();
        Player startRotatePlayer = player.player();

        startRotatePlayer.makeMove(this, location, validMoves(colour), this);

    }


    // Returns the list of spectatators.
    @Override
    public Collection<Spectator> getSpectators() {
        return Collections.unmodifiableCollection(spectators);
    }


    // Cycles through all ScotlandYardPlayers and adds their colours to a list which si then returned.
    @Override
    public List<Colour> getPlayers() {
        List<Colour> playerColours = new ArrayList<>();
        for (ScotlandYardPlayer syplayer : syplayers) {
            playerColours.add(syplayer.colour());
        }

        return Collections.unmodifiableList(playerColours);
    }


    // Method that returns a list of the colours of the winning players.
    @Override
    public Set<Colour> getWinningPlayers() {

        // Set up a set for the colours and define two booleans used to determine who wins.
        Set<Colour> winningPlayers = new HashSet<>();
        boolean dWin = false;
        boolean xWin = false;

        // Get Mr X as a ScotlandYardPlayer.
        ScotlandYardPlayer mrX = null;
        for (ScotlandYardPlayer syplayer : syplayers) {
            if (syplayer.isMrX()) {
                mrX = syplayer;
                break;
            }
        }
        if (mrX == null) throw new AssertionError("Mr.X does not exist");


        // Checks if Mr X has been caught, and makes dWin = true if so.
        for (ScotlandYardPlayer syplayer : syplayers) {

            if (syplayer.location() == mrX.location()) dWin = true;
        }


        //Checks if Mr X is stuck, and makes dWin = true if so.
        if((validMoves(BLACK).size() == 0) && (currentPlayerIndex == 0)) 	dWin = true;




        //Checks if the round limit has been reached and makes xWin = true if so.
        if (currentRound >= rounds.size() && currentPlayerIndex == 0) xWin = true;


        //Checks if detectives are stuck (all detectives only have pass moves), and makes xWin = true if so.
        boolean noValidDetectiveMoves = true;
        for (ScotlandYardPlayer syplayer : syplayers) {
            Set<Move> moves = validMoves(syplayer.colour());
            if ((syplayer.colour() != Colour.BLACK) &&
                    !(moves.size() == 1 && moves.iterator().next() instanceof PassMove))
                noValidDetectiveMoves = false;
        }
        if (noValidDetectiveMoves) xWin = true;



        //If the game is over, check which boolean = true and return the appopriate players as the winners.
        if (isGameOver()) {
            if (dWin) {
                Set<Colour> detectives = new HashSet<>();
                for (ScotlandYardPlayer syplayer : syplayers) {
                    if (syplayer.colour() != BLACK) detectives.add(syplayer.colour());
                }
                winningPlayers = detectives;
            }

            if (xWin) {
                Set<Colour> x = new HashSet<>();
                x.add(Colour.BLACK);
                winningPlayers = x;
            }
        }


        // If the game is not over, return an empty set.
        return Collections.unmodifiableSet(winningPlayers);

    }

    //Returns the player location as an optional integer.
    @Override
    public Optional<Integer> getPlayerLocation(Colour colour) {

        //Returns Mr X location
        if (colour == Colour.BLACK) {
            if (this.mrXLastKnown == 0) {
                return (Optional.of(0));
            }
            else return Optional.of(this.mrXLastKnown);
        }

        // Retruns detective location.
         else if ((colour == Colour.BLUE) || (colour == Colour.GREEN) || (colour == Colour.RED)
                || (colour == Colour.WHITE) || (colour == Colour.YELLOW)) {
            for (ScotlandYardPlayer syplayer : syplayers) {
                if (colour.equals(syplayer.colour())) {
                    return Optional.of(syplayer.location());
                }
            }
        }

        // Returns empty if colour isnt valid.
        return Optional.empty();

    }


    //Gets the number of tickets of a player as an optional integer.
    @Override
    public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {
        for (ScotlandYardPlayer syplayer : syplayers) {
            if (colour.equals(syplayer.colour())) {
                return Optional.of(syplayer.tickets().get(ticket));
            }
        }

        //Returns empty if colour isnt valid.
        return Optional.empty();
    }

    // Method to check if the game is over.
    @Override
    public boolean isGameOver() {

      // Gets Mr X
        ScotlandYardPlayer mrX = null;
        for (ScotlandYardPlayer syplayer : syplayers) {
            if (syplayer.isMrX()) {
                mrX = syplayer;
                break;
            }
        }
        if (mrX == null) throw new AssertionError("Mr.X does not exist");

        //If Mr X is caught then retruns true
        for (ScotlandYardPlayer player : syplayers) {
            if (player.isDetective() && player.location() == mrX.location()) return true;
        }

        // If the round limit is reached then returns true
        ScotlandYardPlayer player = syplayers.get(currentPlayerIndex);
        if ((currentRound == rounds.size()) && player.isMrX()) return true;

        //If the detectives are stuck then returns true
        boolean noValidDetectiveMoves = true;
        for (ScotlandYardPlayer syplayer : syplayers) {
            Set<Move> moves = validMoves(syplayer.colour());
            if ((syplayer.colour() != Colour.BLACK) &&
                    !(moves.size() == 1 && moves.iterator().next() instanceof PassMove))
                noValidDetectiveMoves = false;
        }
        if (noValidDetectiveMoves) return true;

        //If Mr X is stuck then returns true
        if (player.isMrX() && validMoves(Colour.BLACK).isEmpty()) return true;

        //Retruns false if none of the game ending conditions are met.
        return false;

    }


    // Returns the current player
    @Override
    public Colour getCurrentPlayer() {
        return syplayers.get(currentPlayerIndex).colour();
    }


    //Retruns the current round
    @Override
    public int getCurrentRound() {
        return this.currentRound;
    }


    //Retruns the list of rounds
    @Override
    public List<Boolean> getRounds() {
        return Collections.unmodifiableList(this.rounds);
    }


    //Returns the game graph
    @Override
    public Graph<Integer, Transport> getGraph() {
        ImmutableGraph<Integer, Transport> imGraph = new ImmutableGraph<>(this
                .graph);
        return imGraph;
    }



    @Override
    public void accept(Move move) {

        // Checks for NULL moves and invalid moves.
        if (move == null) throw new NullPointerException("NULL move");
        if (!validMoves(getCurrentPlayer()).contains(move)) throw new IllegalArgumentException("The move is not in the valid move set.");

        // Gets the relevant player and ScotlandYardView for later calling in the visit methods.
        ScotlandYardPlayer movePlayer = colourToPlayer(move.colour());
        ScotlandYardView syView = this;

        //Increments the player index and checks if the index is the same size as the list of players, in which case reverts back to Mr X.
        currentPlayerIndex++;
        if (currentPlayerIndex == syplayers.size()) currentPlayerIndex = 0;


        //Calls .visit. MoveVisitor methods are overidden:
        move.visit(new MoveVisitor() {

            // For PassMoves the spectators are just notified.
            @Override
            public void visit(PassMove move) {
                for (Spectator spectator : spectators) {
                    spectator.onMoveMade(syView, move);
                }
            }

            // For TicketMoves:
            @Override
            public void visit(TicketMove move) {

                //Sets the players location to the move destiantion.
                movePlayer.location(move.destination());

                //If Mr X:
                if (movePlayer.colour() == Colour.BLACK) {

                    //Checks if the round should reveal his location, and if so update mrXLastKnown.
                    boolean reveal = rounds.get(currentRound);
                    if (reveal) {
                        mrXLastKnown = move.destination();
                    }

                    //Creates a new TicketMove, increments the currentRound and removes the ticket used from the player.
                    TicketMove hidden = new TicketMove(Colour.BLACK, move.ticket(), mrXLastKnown);
                    currentRound++;
                    movePlayer.removeTicket(move.ticket());

                    //Informs the spectators .onRoundStarted
                    for (Spectator spectator : spectators) {
                        spectator.onRoundStarted(syView, currentRound);
                    }

                    //If it is a reveal round then spectators are updated onMoveMade with the TicketMove move paramtre.
                    if (reveal){
                      for (Spectator spectator : spectators) {
                          spectator.onMoveMade(syView, move);
                      }
                    }

                    // If it is not a reveal round then the spectators are updated as before but with the hidden TicketMove to hide Mr X's location.
                    if(!reveal){
                    for (Spectator spectator : spectators) {
                        spectator.onMoveMade(syView, hidden);
                      }
                    }
                }

                //If it is not Mr X then we remove the ticket from the player and add that ticket to Mr X.
                else {
                    movePlayer.removeTicket(move.ticket());
                    for (ScotlandYardPlayer syplayer : syplayers) {
                        if (syplayer.colour() == Colour.BLACK) syplayer.addTicket(move.ticket());
                    }

                    //Update the spectators .onMoveMade
                    for (Spectator spectator : spectators) {
                        spectator.onMoveMade(syView, move);
                    }
                }

            }

            // For double moves:
            @Override
            public void visit(DoubleMove move) {

                //Deal with the first move:
                TicketMove first = move.firstMove();

                //If not a reveal round make the first move one that hides Mr X's location.
                if (!rounds.get(currentRound)) {
                    first = new TicketMove(move.colour(), move.firstMove().ticket(), mrXLastKnown);
                }

                //Deal with the second move:
                TicketMove second = move.secondMove();

                //If the next round is not a reveal round but the current one is make a ticket that updates Mr X location to the first move.
                //If the current move is not a reveal round then make a move that keeps Mr X location to mrXLastKnown.
                if (!(rounds.get(currentRound + 1))) {
                    if (rounds.get(currentRound)) {
                        second = new TicketMove(move.colour(), move.secondMove().ticket(), move.firstMove().destination());
                    } else {
                        second = new TicketMove(move.colour(), move.secondMove().ticket(), mrXLastKnown);
                    }
                }

                //Create a DoubleMove with the two moves.
                DoubleMove doubleMove = new DoubleMove(move.colour(), first, second);

                //Remove the double ticket.
                movePlayer.removeTicket(Ticket.DOUBLE);

                //Update the spectatators .onMoveMade with a doublemove parametre.
                for (Spectator spectator : spectators) {
                    spectator.onMoveMade(syView, doubleMove);
                }

                //Implement all the visit logic for the first move.
                visit(move.firstMove());

                //Implement all the visit logic for the second move.
                visit(move.secondMove());

            }

        });

        //If the game is over then update spectators .onGameOver.
        if (isGameOver()) {
            for (Spectator spectator : spectators) {
                spectator.onGameOver(this, getWinningPlayers());
            }

        }

        //If not, check if the next player is a detective, and if so call makeMove.
        else {
            ScotlandYardPlayer next = syplayers.get(currentPlayerIndex);
            if (next.isDetective()) {
                next.player().makeMove(this, next.location(), validMoves(getCurrentPlayer()), this);
            }

            //If the next player is not a detetctive update spectators .onRotationComplete.
            else {
                for (Spectator spectator : spectators) {
                    spectator.onRotationComplete(this);
                }
            }
        }

    }

    //Returns all the valid moves for a player:
    private Set<Move> validMoves(Colour playerColour) {

        //Gets the ScotlandYardPlayer, creates a new set for the moves and gets all the valid tickets for the player.
        ScotlandYardPlayer syplayer = colourToPlayer(playerColour);
        Set<Move> validMoves = new HashSet<>();
        Set<Move> ticketMoves = validTicket(syplayer, syplayer.location());

        //For every valid ticket:
        for (Move move : ticketMoves) {

            //Add the ticketMove to the valid moves
            validMoves.add(move);

            //If the player has a double ticket and there are enough rounds left to play a double ticket:
            if (syplayer.hasTickets(Ticket.DOUBLE, 1) && ((rounds.size() - 1) > currentRound)) {

                //Cast the move as a TicketMove and get the valid tickets for the second move
                TicketMove tMove = (TicketMove) move;
                Set<Move> doubleMoves = validTicket(syplayer, tMove.destination());

                //For the  second move:
                for (Move dMove : doubleMoves) {

                    //Cast the second move as a ticketMove and create a new DoubleMove
                    TicketMove tMove2 = (TicketMove) dMove;
                    DoubleMove doubleMove = new DoubleMove(playerColour, tMove.ticket(), tMove
                            .destination(), tMove2.ticket(), tMove2.destination());

                    //Check if the first and second move are of the same type, and if so check that they have two of that ticket.
                    //If all is well, ass to the validMoves.
                    boolean sameType = (doubleMove.firstMove().ticket().compareTo(doubleMove
                            .secondMove().ticket()) == 0);
                    if ((sameType && syplayer.hasTickets(doubleMove.firstMove().ticket(), 2)) || !sameType) {
                        validMoves.add(doubleMove);
                    }


                }
            }
        }

        //If the player is not Mr X and has no validMoves, add a pass move.
        if (playerColour != Colour.BLACK && validMoves.isEmpty()) {
            Move pass = new PassMove(playerColour);
            validMoves.add(pass);
        }

        //Return the valid moves.
        return Collections.unmodifiableSet(validMoves);
    }

    //Returns the valid tickets a playr can use:
    private Set<Move> validTicket(ScotlandYardPlayer player, int currentLocation) {

        //Create a set for the valid tickets and get the edges foe the current location from the graph.
        Set<Move> validTicket = new HashSet<>();
        Collection<Edge<Integer, Transport>> edges = graph.getEdgesFrom(graph.getNode
                (currentLocation));

        //For every edge:
        for (Edge<Integer, Transport> edge : edges) {

            //Define what ticket type and where it leads
            Ticket typeOfTransport = Ticket.fromTransport(edge.data());
            int targetLocation = edge.destination().value();

            //If the player has the right ticekt, or a secret ticket:
            if (player.hasTickets(typeOfTransport, 1) || player.hasTickets(Ticket.SECRET, 1)) {

            //Checks if the target location has a detective in it
            boolean notOccupied = true;
            for (ScotlandYardPlayer p : syplayers) {
                if (p.isDetective()) {
                    if (p.location() == targetLocation) {
                        notOccupied = false;
                        break;
                    }
                }
            }

            //If the target loaction has no detective in it: and the player has enough tickets, create a nee Move and add it to the list.
            if (notOccupied) {

                //If the player has enough tickets, create a new Move and add it to the list.
                if (player.hasTickets(typeOfTransport, 1)) {
                    Move move = new TicketMove(player.colour(), typeOfTransport, targetLocation);
                    validTicket.add(move);
                }

                //Same but if the player has a SECRET ticket.
                if (player.hasTickets(Ticket.SECRET, 1)) {
                    Move move = new TicketMove(player.colour(), Ticket.SECRET, targetLocation);
                    validTicket.add(move);
                }
            }


        }}

        //Return the set.
        return validTicket;
    }

}
