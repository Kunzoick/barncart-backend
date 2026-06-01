package com.zoick.farmmarket.domain.cart;
import com.zoick.farmmarket.domain.produce.Listing;
import com.zoick.farmmarket.domain.produce.ListingRepository;
import com.zoick.farmmarket.domain.user.User;
import com.zoick.farmmarket.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
//contains all cart business logic, The most important rule enforced here is check before insert
//if the listing is already in the cart, update quantity instead of inserting
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;

    //Internal only
    private Cart getOrCreateCart(UUID userId){
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user= userRepository.getReferenceById(userId);
                    Cart cart= new Cart();
                    cart.setUser(user);
                    return cartRepository.save(cart);
                });
    }
    //never creates a cart
    @Transactional
    public CartResponse getCart(UUID userId){
        return cartRepository.findByUserId(userId).map(cart -> {
            List<CartItemResponse> items= cartItemRepository.findAllByCartIdWithDetails(cart.getId()).stream()
                    .map(CartItemResponse::from).collect(Collectors.toList());
            return CartResponse.from(cart, items);
        }).orElseGet(() -> CartResponse.empty(userId));
    }
   @Transactional
   public CartResponse addItem(UUID userId, AddCartItemRequest request){
        Cart cart= getOrCreateCart(userId);
        Listing listing= listingRepository.findByIdWithDetails(request.getListingId()).orElseThrow(() ->
                new IllegalArgumentException("Listing not found: "+ request.getListingId()));
        if(!listing.isActive()){
            throw new IllegalArgumentException("listing is no longer active");
        }
        //Duplicate check
       CartItem cartItem= cartItemRepository.findByCartIdAndListingId(cart.getId(), listing.getId()).orElseGet(() ->
       {
           CartItem newItem= new CartItem();
           newItem.setCart(cart);
           newItem.setListing(listing);
           newItem.setQuantity(BigDecimal.ZERO);
           return newItem;
       });
        BigDecimal newtotal= cartItem.getQuantity().add(request.getQuantity());
        //soft stock check(catches obvious overshoots before checkout)
       BigDecimal available = listing.getBatch().getQuantityAvailable();
       if(newtotal.compareTo(available) > 0){
           throw new IllegalArgumentException("Requested quantity exceeds available stock. Available: "+ available);
       }
        cartItem.setQuantity(newtotal);
        cartItemRepository.save(cartItem);
        //Build response directly
       List<CartItemResponse> items= cartItemRepository.findAllByCartIdWithDetails(cart.getId()).stream().map(CartItemResponse::from)
               .collect(Collectors.toList());
       return CartResponse.from(cart, items);
   }
   @Transactional
   public CartResponse updateItem(UUID userId, UUID cartItemId, UpdateCartItemRequest request){
    //single indexed query
       if(!cartItemRepository.existsByIdAndCartUserId(cartItemId, userId)){
           throw new IllegalArgumentException("Cart item does not belong to this user");
       }
       CartItem cartItem= cartItemRepository.findById(cartItemId).orElseThrow(() -> new IllegalArgumentException("Cart item not found: "+ cartItemId));
       cartItem.setQuantity(request.getQuantity());
       cartItemRepository.save(cartItem);

       List<CartItemResponse> items= cartItemRepository.findAllByCartIdWithDetails(cartItem.getCart().getId()).stream().map(CartItemResponse::from)
               .collect(Collectors.toList());
       return CartResponse.from(cartItem.getCart(), items);
   }
   @Transactional
    public CartResponse removeItem(UUID userId, UUID cartItemId){
     //single indexed query
       CartItem cartItem= cartItemRepository.findByIdAndUserId(cartItemId, userId).orElseThrow(() -> new IllegalArgumentException("" +
               "Cart item does not belong to this user"));
       Cart cart= cartItem.getCart();
       cartItemRepository.delete(cartItem);

       List<CartItemResponse> items= cartItemRepository.findAllByCartIdWithDetails(cart.getId()).stream().map(CartItemResponse::from)
               .collect(Collectors.toList());
       return CartResponse.from(cart, items);
    }
    @Transactional
    public void clearCart(UUID userId){
        cartRepository.findByUserId(userId).ifPresent(cart -> cartItemRepository.deleteAllByCartId(cart.getId()));
    }
}
