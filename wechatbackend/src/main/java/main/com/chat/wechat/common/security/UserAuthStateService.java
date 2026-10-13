package main.com.chat.wechat.common.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import main.com.chat.wechat.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserAuthStateService {
	private final UserRepository userRepository;
	private final AuthStateCache cache;
	private final Counter databaseLookups;

	public UserAuthStateService(
			UserRepository userRepository,
			AuthStateCache cache,
			MeterRegistry meterRegistry) {
		this.userRepository = userRepository;
		this.cache = cache;
		this.databaseLookups = Counter.builder("auth_state_database_lookups")
				.description("Authoritative user authentication-state lookups")
				.register(meterRegistry);
	}

	public Optional<AuthState> find(UUID userId) {
		AuthStateCache.CacheLookup lookup = cache.lookup(userId);
		if (lookup.state().isPresent()) {
			return lookup.state();
		}

		databaseLookups.increment();
		Optional<AuthState> state = userRepository.findById(userId)
				.map(user -> new AuthState(user.id(), user.tokenVersion(), user.active()));
		state.ifPresent(cache::put);
		return state;
	}
}
