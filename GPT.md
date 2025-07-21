# 🚀 GPT-4 SETUP COMMANDS

# 1. Set environment variables

export OPENAI_API_KEY="sk-your-actual-key-here"

# 2. Choose model (theo thứ tự từ rẻ → đắt):

# OPTION 1: GPT-4o-mini (Recommended cho demo) - ~$0.15/1M tokens

export OPENAI_MODEL="gpt-4o-mini"

# OPTION 2: GPT-4o (Balanced) - ~$2.50/1M tokens

export OPENAI_MODEL="gpt-4o"

# OPTION 3: GPT-4 Turbo (High quality) - ~$10/1M tokens

export OPENAI_MODEL="gpt-4-turbo"

# OPTION 4: GPT-4 (Most expensive) - ~$30/1M tokens

export OPENAI_MODEL="gpt-4"

# 3. Test cost với model khác nhau:

# Test với GPT-4o-mini (recommended)

curl -X POST http://localhost:8080/api/chatgpt/chat \
 -H "Content-Type: application/json" \
 -d '{"message": "Điểm chuẩn CNTT?"}'

# Check cost sau mỗi query

curl -X GET http://localhost:8080/api/chatgpt/cost-dashboard

# 4. MODEL COMPARISON TABLE:

# | Model | Cost/1M tokens | Quality | Speed | $10 Budget |

# |--------------|----------------|---------|-------|------------|

# | gpt-4o-mini | $0.15 | Good | Fast | ~50,000 queries |

# | gpt-4o | $2.50 | Great | Fast | ~3,000 queries |

# | gpt-4-turbo | $10.00 | Excellent| Medium| ~800 queries |

# | gpt-4 | $30.00 | Best | Slow | ~250 queries |

# 5. RECOMMENDATION cho demo $10:

# For DEMO: gpt-4o-mini

# - Quality tốt hơn GPT-3.5 nhiều

# - Chi phí chỉ hơn 3.5-turbo một chút

# - Đủ xài demo 1-2 tuần

# For PRODUCTION DEMO: gpt-4o

# - Quality rất tốt

# - Chi phí vừa phải

# - Đủ xài demo 2-3 ngày intensive

# 6. Dynamic model switching (nâng cao):

# Start với gpt-4o-mini

export OPENAI_MODEL="gpt-4o-mini"

# Nếu cần quality cao hơn cho specific queries:

export OPENAI_MODEL="gpt-4o"

# 7. Test commands:

# Simple test

curl -X POST localhost:8080/api/chatgpt/chat \
 -H "Content-Type: application/json" \
 -d '{"message": "Hello GPT-4!"}'

# Education test

curl -X POST localhost:8080/api/chatgpt/chat \
 -H "Content-Type: application/json" \
 -d '{"message": "Tôi có 24 điểm khối A00, nên chọn trường nào?"}'

# Complex test

curl -X POST localhost:8080/api/chatgpt/chat \
 -H "Content-Type: application/json" \
 -d '{"message": "So sánh ngành Công nghệ thông tin giữa trường Bách Khoa Hà Nội và ĐH Công nghệ - ĐHQGHN về điểm chuẩn, chất lượng đào tạo"}'

# 8. Monitor cost real-time:

watch -n 5 'curl -s localhost:8080/api/chatgpt/cost-dashboard | jq ".total_cost_usd, .remaining_budget"'

# 9. Emergency stop (nếu cost quá cao):

# Set model về 3.5-turbo:

export OPENAI_MODEL="gpt-3.5-turbo"

# Hoặc stop application:

pkill -f "spring-boot:run"
